cmake_minimum_required(VERSION 3.2)

# JNI CMake for :core. Lives in this repo so pylon's nested ZeroTierOne
# (stale java/CMakeLists.txt still lists removed lz4/json-parser/Defaults)
# does not need a submodule patch. Source list matches ZeroTierOne 1596029.
project(ZeroTierOneJNI)

set(ZTO "${CMAKE_CURRENT_SOURCE_DIR}/../libzt/ext/ZeroTierOne")

find_package(Java COMPONENTS Development)
message("JAVA_HOME: $ENV{JAVA_HOME}")

if(WIN32)
    set(Java_INCLUDE_DIRS $ENV{JAVA_HOME}/include)
endif()

if(APPLE)
    set(Java_INCLUDE_DIRS "/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX10.10.sdk/System/Library/Frameworks/JavaVM.framework/Headers")
endif()

message("Java Include Dirs: ${Java_INCLUDE_DIRS}")

if(WIN32)
    add_definitions(-DNOMINMAX)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} /EHsc /W3 /MP")
endif()

if(APPLE)
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -arch i386 -arch x86_64 -Wall -O3 -flto -fPIE -fvectorize -fstack-protector -mmacosx-version-min=10.7 -Wno-unused-private-field")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${CMAKE_C_FLAGS} -fno-rtti")
endif()

set(src_files
    ${ZTO}/node/AES.cpp
    ${ZTO}/node/AES_aesni.cpp
    ${ZTO}/node/AES_armcrypto.cpp
    ${ZTO}/node/Bond.cpp
    ${ZTO}/node/C25519.cpp
    ${ZTO}/node/Capability.cpp
    ${ZTO}/node/CertificateOfMembership.cpp
    ${ZTO}/node/CertificateOfOwnership.cpp
    ${ZTO}/node/Identity.cpp
    ${ZTO}/node/IncomingPacket.cpp
    ${ZTO}/node/InetAddress.cpp
    ${ZTO}/node/Membership.cpp
    ${ZTO}/node/Metrics.cpp
    ${ZTO}/node/Multicaster.cpp
    ${ZTO}/node/Network.cpp
    ${ZTO}/node/NetworkConfig.cpp
    ${ZTO}/node/Node.cpp
    ${ZTO}/node/OutboundMulticast.cpp
    ${ZTO}/node/Packet.cpp
    ${ZTO}/node/Path.cpp
    ${ZTO}/node/Peer.cpp
    ${ZTO}/node/Poly1305.cpp
    ${ZTO}/node/Revocation.cpp
    ${ZTO}/node/Salsa20.cpp
    ${ZTO}/node/SelfAwareness.cpp
    ${ZTO}/node/SHA512.cpp
    ${ZTO}/node/Switch.cpp
    ${ZTO}/node/Tag.cpp
    ${ZTO}/node/Topology.cpp
    ${ZTO}/node/Trace.cpp
    ${ZTO}/node/Utils.cpp
    ${ZTO}/osdep/Arp.cpp
    ${ZTO}/osdep/EthernetTap.cpp
    ${ZTO}/osdep/Http.cpp
    ${ZTO}/osdep/ManagedRoute.cpp
    ${ZTO}/osdep/NeighborDiscovery.cpp
    ${ZTO}/osdep/LinuxEthernetTap.cpp
    ${ZTO}/osdep/LinuxNetLink.cpp
    ${ZTO}/osdep/OSUtils.cpp
    ${ZTO}/osdep/PortMapper.cpp
    ${ZTO}/ext/http-parser/http_parser.c
    ${ZTO}/java/jni/com_zerotierone_sdk_Node.cpp
    ${ZTO}/java/jni/ZT_jniutils.cpp
    ${ZTO}/java/jni/ZT_jnicache.cpp
)

set(include_dirs
    ${ZTO}/include/
    ${ZTO}/node/
    ${ZTO}/ext/prometheus-cpp-lite-1.0/simpleapi/include/
    ${ZTO}/ext/prometheus-cpp-lite-1.0/core/include/
    ${ZTO}/ext/prometheus-cpp-lite-1.0/3rdpatry/http-client-lite/include/
    ${ZTO}/ext/
    ${Java_INCLUDE_DIRS})

if(WIN32)
    set(include_dirs
        ${include_dirs}
        ${Java_INCLUDE_DIRS}/win32)
endif()

include_directories(${include_dirs})

add_library(${PROJECT_NAME} SHARED ${src_files})

if(APPLE)
    set_target_properties(${PROJECT_NAME} PROPERTIES SUFFIX ".jnilib")
endif()

set(link_libs)

if(WIN32)
    set(link_libs
        wsock32
        ws2_32
    )
endif()

target_link_libraries(${PROJECT_NAME} ${link_libs} log)
# NDK 25 does not honor ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES. 16KB devices
# (Android 15+) reject LOAD align 0x1000 on dlopen.
target_link_options(${PROJECT_NAME} PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384")
