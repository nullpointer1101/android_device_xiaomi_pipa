/*
 * Copyright (C) 2023-2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include <android/log.h>
#include <ctype.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <pthread.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/eventfd.h>
#include <sys/inotify.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>

const char kPackageName[] = "xiaomi-keyboard";

/********************************************
 * Configuration Constants
 ********************************************/
#define BUFFER_SIZE 256
#define NANODEV_PATH "/dev/nanodev0"
#define VERSION_STRING "2.1.0"

/********************************************
 * Message Protocol Definitions
 ********************************************/
#define MSG_TYPE_SLEEP 37
#define MSG_TYPE_WAKE 40
#define MSG_HEADER_1 0x31
#define MSG_HEADER_2 0x38

// Logging macros
#define TAG "xiaomi-keyboard"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

#define LOG_IMPORTANT(fmt, ...) do { \
    time_t now = time(NULL); \
    struct tm* tm_info = localtime(&now); \
    char time_str[20]; \
    strftime(time_str, sizeof(time_str), "%Y-%m-%d %H:%M:%S", tm_info); \
    __android_log_print(ANDROID_LOG_INFO, TAG, "[%s] " fmt, time_str, ##__VA_ARGS__); \
} while(0)

// Global state
int fd = -1;
int wake_event_fd = -1;
bool kb_status = true;
volatile sig_atomic_t terminate = 0;
time_t last_monitor_activity = 0;

// Thread synchronization
pthread_mutex_t kb_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t kb_cond = PTHREAD_COND_INITIALIZER;
bool kb_thread_paused = false;

/**
 * Wake up monitor thread blocked in poll()
 */
static void notify_monitor_thread() {
    if (wake_event_fd != -1) {
        uint64_t val = 1;
        write(wake_event_fd, &val, sizeof(val));
    }
}

/**
 * Check if Xiaomi keyboard is connected via magnetic pogo pins.
 * On pipa (Xiaomi Pad 6), the hardware dock dynamically registers at /dev/input/event12.
 */
bool is_keyboard_connected(char* out_path, size_t out_path_size) {
    const char* default_path = "/dev/input/event12";
    if (access(default_path, F_OK) == 0) {
        if (out_path && out_path_size > 0) {
            snprintf(out_path, out_path_size, "%s", default_path);
        }
        return true;
    }

    return false;
}

/**
 * Set keyboard state via /dev/nanodev0
 */
void set_kb_state(bool value, bool force) {
    if (kb_status != value || force) {
        kb_status = value;
        LOGI("Setting keyboard state to: %d", value);
        unsigned char buf[3] = {0x32, 0xFF, (unsigned char)value};
        if (fd != -1 && write(fd, &buf, 3) != 3) {
            LOGE("Failed to write keyboard state");
        }
    }
}

/**
 * Responsive, event-driven keyboard monitoring thread using Linux inotify
 */
void *keyboard_monitor_thread(void *arg) {
    (void)arg;

    LOGI("Keyboard monitor thread started (inotify event-driven mode)");

    int inotify_fd = inotify_init1(IN_CLOEXEC | IN_NONBLOCK);
    if (inotify_fd < 0) {
        LOGE("Failed to create inotify instance: %s", strerror(errno));
        return NULL;
    }

    int watch_desc = inotify_add_watch(inotify_fd, "/dev/input", IN_CREATE | IN_DELETE);
    if (watch_desc < 0) {
        LOGE("Failed to add inotify watch on /dev/input: %s", strerror(errno));
        close(inotify_fd);
        return NULL;
    }

    struct pollfd pfds[2];
    pfds[0].fd = inotify_fd;
    pfds[0].events = POLLIN;
    pfds[1].fd = wake_event_fd;
    pfds[1].events = POLLIN;

    while (!terminate) {
        // 1. Suspend monitor thread while device is in sleep (zero CPU wakeups)
        pthread_mutex_lock(&kb_mutex);
        while (kb_thread_paused && !terminate) {
            LOGI("Keyboard monitor thread sleeping on condition variable");
            pthread_cond_wait(&kb_cond, &kb_mutex);
            if (!kb_thread_paused) {
                LOGI("Keyboard monitor thread resumed from sleep");
            }
        }
        pthread_mutex_unlock(&kb_mutex);

        if (terminate) break;

        // 2. Wait indefinitely until inotify or wake event occurs (zero idle wakeups)
        int poll_res = poll(pfds, (wake_event_fd != -1 ? 2 : 1), -1);
        if (poll_res < 0) {
            if (errno == EINTR) continue;
            LOGE("poll() error: %s", strerror(errno));
            break;
        }

        if (terminate) break;

        // 3. Drain wake_event_fd if signaled
        if (wake_event_fd != -1 && (pfds[1].revents & POLLIN)) {
            uint64_t val = 0;
            read(wake_event_fd, &val, sizeof(val));
            continue;
        }

        // 4. Handle inotify events when device nodes change in /dev/input
        if (pfds[0].revents & POLLIN) {
            // Settle brief mechanical bounce (50ms) before evaluating state
            poll(pfds, (wake_event_fd != -1 ? 2 : 1), 50);

            // Drain all pending inotify events
            char inotify_buf[1024] __attribute__((aligned(__alignof__(struct inotify_event))));
            while (read(inotify_fd, inotify_buf, sizeof(inotify_buf)) > 0) {}

            char kb_path[128] = {0};
            bool connected = is_keyboard_connected(kb_path, sizeof(kb_path));

            pthread_mutex_lock(&kb_mutex);
            last_monitor_activity = time(NULL);

            if (!kb_thread_paused) {
                if (connected && !kb_status) {
                    LOGI("Keyboard connected (%s) - enabling", kb_path);
                    set_kb_state(true, false);
                } else if (!connected && kb_status) {
                    LOGI("Keyboard disconnected - disabling");
                    set_kb_state(false, false);
                }
            }
            pthread_mutex_unlock(&kb_mutex);
        }
    }

    inotify_rm_watch(inotify_fd, watch_desc);
    close(inotify_fd);

    LOGI("Keyboard monitor thread exiting");
    return NULL;
}

/**
 * Handle power events (wake/sleep) from /dev/nanodev0
 */
void handle_power_event(char *buffer) {
    bool is_wake = (buffer[6] == 1);

    pthread_mutex_lock(&kb_mutex);
    if (is_wake) {
        kb_thread_paused = false;
        last_monitor_activity = time(NULL);
        pthread_cond_signal(&kb_cond);
    } else {
        kb_thread_paused = true;
    }
    pthread_mutex_unlock(&kb_mutex);
    notify_monitor_thread();

    if (is_wake) {
        LOGI("Wake event - checking keyboard state");
        char kb_path[128] = {0};
        bool keyboard_connected = is_keyboard_connected(kb_path, sizeof(kb_path));
        LOGI("Keyboard %s on wake (%s)", keyboard_connected ? "connected" : "disconnected",
             keyboard_connected ? kb_path : "none");

        pthread_mutex_lock(&kb_mutex);
        if (keyboard_connected) {
            set_kb_state(true, true);
        } else {
            kb_status = false;
        }
        pthread_mutex_unlock(&kb_mutex);
    } else {
        LOGI("Sleep event - paused keyboard monitoring");
    }
}

/**
 * Main event handler
 */
void handle_event(char *buffer, ssize_t bytes_read) {
    if (bytes_read < 7 || buffer[1] != MSG_HEADER_1 || buffer[2] != MSG_HEADER_2) {
        return;
    }

    if ((buffer[4] == MSG_TYPE_SLEEP || buffer[4] == MSG_TYPE_WAKE) && buffer[5] == 1) {
        handle_power_event(buffer);
    }
}

/**
 * Attempt to reconnect to /dev/nanodev0
 */
int reconnect_device() {
    int attempts = 0;
    const int max_attempts = 5;
    int new_fd = -1;

    LOGI("Starting device reconnection");

    while (attempts < max_attempts && new_fd == -1 && !terminate) {
        LOGI("Reconnect attempt %d/%d", attempts + 1, max_attempts);
        new_fd = open(NANODEV_PATH, O_RDWR);

        if (new_fd != -1) {
            LOGI("Successfully reconnected");
            return new_fd;
        }

        int sleep_time = (attempts < 3) ? (1 << attempts) : 4;
        sleep(sleep_time);
        attempts++;
    }

    LOGE("Failed to reconnect after %d attempts", attempts);
    return -1;
}

/**
 * Signal handler
 */
void signal_handler(int signum) {
    LOGI("Caught signal %d, terminating...", signum);
    terminate = 1;
    pthread_mutex_lock(&kb_mutex);
    pthread_cond_signal(&kb_cond);
    pthread_mutex_unlock(&kb_mutex);
    notify_monitor_thread();
}

/**
 * Cleanup resources
 */
void cleanup_resources(pthread_t monitor_thread) {
    LOGI("Performing cleanup...");

    pthread_mutex_lock(&kb_mutex);
    terminate = 1;
    pthread_cond_signal(&kb_cond);
    pthread_mutex_unlock(&kb_mutex);
    notify_monitor_thread();

    pthread_join(monitor_thread, NULL);

    if (wake_event_fd != -1) {
        close(wake_event_fd);
        wake_event_fd = -1;
    }

    if (fd != -1) {
        close(fd);
        fd = -1;
    }
}

/**
 * Main function
 */
int main() {
    time_t start_time = time(NULL);
    struct tm* tm_info = localtime(&start_time);
    char time_str[64];
    strftime(time_str, sizeof(time_str), "%Y-%m-%d %H:%M:%S", tm_info);

    LOG_IMPORTANT("Xiaomi keyboard service v%s starting at %s", VERSION_STRING, time_str);
    LOG_IMPORTANT("This service is user-controlled via Settings > Connected devices");

    wake_event_fd = eventfd(0, EFD_CLOEXEC | EFD_NONBLOCK);
    if (wake_event_fd == -1) {
        LOGE("Failed to create wake eventfd: %s", strerror(errno));
    }

    // Open nanodev
    fd = open(NANODEV_PATH, O_RDWR);
    if (fd == -1) {
        LOGE("Error opening nanodev device: %s", strerror(errno));
        if (wake_event_fd != -1) close(wake_event_fd);
        return errno;
    }

    // Check initial keyboard status
    char init_path[128] = {0};
    bool initial_connected = is_keyboard_connected(init_path, sizeof(init_path));
    if (!initial_connected) {
        kb_status = false;
        LOGW("Keyboard not found, starting disabled");
    } else {
        LOGI("Keyboard found (%s), starting enabled", init_path);
        set_kb_state(true, true);
    }

    // Create monitor thread
    pthread_t monitor_thread;
    if (pthread_create(&monitor_thread, NULL, keyboard_monitor_thread, NULL) != 0) {
        LOGE("Failed to create monitor thread");
        close(fd);
        if (wake_event_fd != -1) close(wake_event_fd);
        return EXIT_FAILURE;
    }

    // Setup signal handling
    signal(SIGINT, signal_handler);
    signal(SIGTERM, signal_handler);

    // Main event loop
    ssize_t bytes_read;
    char buffer[BUFFER_SIZE];
    int recoveries = 0;
    const int MAX_RECOVERIES = 3;

    LOGI("Main loop starting");
    while (!terminate) {
        bytes_read = read(fd, buffer, BUFFER_SIZE);

        if (bytes_read > 0) {
            recoveries = 0;
            handle_event(buffer, bytes_read);
        } else if (bytes_read == 0) {
            usleep(100000); // 100ms
        } else {
            LOGE("Error reading device: %s", strerror(errno));

            if (++recoveries > MAX_RECOVERIES) {
                LOGE("Exceeded maximum recovery attempts");
                break;
            }

            close(fd);
            fd = reconnect_device();

            if (fd == -1) {
                LOGE("Could not recover device connection");
                break;
            }
        }
    }

    // Exit status
    time_t end_time = time(NULL);
    double runtime = difftime(end_time, start_time);
    LOGI("Service exiting after %.1f seconds", runtime);

    cleanup_resources(monitor_thread);
    return 0;
}
