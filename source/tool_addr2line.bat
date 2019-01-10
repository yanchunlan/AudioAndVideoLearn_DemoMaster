@echo off
rem current direction
set cur_dir=%cd%

rem addr2line tool path
set add2line_path=D:\android\studio3_2\Sdk\ndk-bundle\toolchains\aarch64-linux-android-4.9\prebuilt\windows-x86_64\bin\aarch64-linux-android-addr2line.exe

rem debug file
set /p debug_file=please input filename:

rem debug_file_path
set debug_file_path=%cur_dir%\%debug_file%

rem debug address
set /p debug_addr=please input pc num:

echo ----------------------- addr2line ------------------------
echo debug filename: %debug_file_path%  PC=%debug_addr%

if exist %debug_file_path% (
%add2line_path% -e %debug_file_path% -f %debug_addr% 
) else (
echo debug file is no exist. 
)

echo ---------------------------------------------------------
pause