
APP_PLATFORM := android-16

#
#APP_ABI := all
#APP_ABI := armeabi armeabi-v7a x86 mips
#APP_ABI := armeabi armeabi-v7a x86
APP_ABI := arm64-v8a x86_64
#APP_ABI := armeabi armeabi-v7a

#APP_STL := gnustl_static
#APP_STL := stlport_static
APP_STL := c++_static

APP_CPPFLAGS += -fexceptions

APP_CPPFLAGS += -Werror=pointer-to-int-cast
APP_CPPFLAGS += -Werror=int-to-pointer-cast
APP_CPPFLAGS += -Werror=shorten-64-to-32

