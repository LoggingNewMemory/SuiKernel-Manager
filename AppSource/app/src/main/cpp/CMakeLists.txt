cmake_minimum_required(VERSION 3.22.1)
project("suikernel_ioctl")

add_library(suikernel_ioctl SHARED SuiKernelIoctl.c)

target_link_libraries(suikernel_ioctl android log)
