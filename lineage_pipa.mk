#
# Copyright (C) 2021 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

# Inherit from those products. Most specific first.
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base.mk)

# Inherit some common lineage stuff.
$(call inherit-product, vendor/lineage/config/common_full_tablet_wifionly.mk)

# Inherit from pipa device
$(call inherit-product, device/xiaomi/pipa/device.mk)

# Axion Flags
TARGET_DISABLE_EPPE := true
TARGET_INCLUDE_AXFX := false
AXION_CAMERA_REAR_INFO := 13
AXION_CAMERA_FRONT_INFO := 8
AXION_PROCESSOR := Snapdragon_870
AXION_MAINTAINER := nullpointer1101
TARGET_INCLUDES_LOS_PREBUILTS := false
TARGET_INCLUDE_PARTNER_SETUP := true
TARGET_INCLUDE_GOOGLE_TELECOMM := false

PRODUCT_NAME := lineage_pipa
PRODUCT_DEVICE := pipa
PRODUCT_MANUFACTURER := Xiaomi
PRODUCT_BRAND := Xiaomi
PRODUCT_MODEL := 23043RP34G

PRODUCT_CHARACTERISTICS := tablet
TARGET_SUPPORTS_QUICK_TAP := false

PRODUCT_GMS_CLIENTID_BASE := android-xiaomi

PRODUCT_BUILD_PROP_OVERRIDES += \
    BuildFingerprint=Xiaomi/pipa_pad_cn/pipa:14/UKQ1.240624.001/OS2.0.20.0.UMZCNXM:user/release-keys  \
