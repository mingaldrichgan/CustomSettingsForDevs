**What is this?**



MODs zip is a complilation of all xml and smali changes made in order to get custom features working on Pixel 4a.

You can download this zip, extract it, and use it to implement the changes on your own Pixel device running stock, or AOSP based, Android 11 ROM.




**Tools you'll need:**


- SuperR's Kitchen (to extract system image from your stock ROM zip and deodex your ROM, if needed)

https://forum.xda-developers.com/t/windows-linux-mac-donate-superrs-kitchen-v3-2-1-2-1-14-2021.3601702/


- Tickle My Android (to decompile/recompile SystemUIGoogle.apk or SystemUI.apk if using other ROM rather than stock Pixel)

https://forum.xda-developers.com/t/tool-tickle-my-android-decompile-recompile-with-ease.1633333/


- Magisk

https://forum.xda-developers.com/t/magisk-the-magic-mask-for-android.3473445/


- VR Theme zip (included in this repository).

- Notepad++

https://notepad-plus-plus.org/downloads/

- WinMerge

https://winmerge.org/




I'm assuming you know what to do with this. Things are already made easy so I'm not going to guide you step by step...well...I will guide you thoguh...




**How-to:**

1. Extract your desired ROM zip (using Kitchen)
2. Fully deodex the ROM if needed (using Kitchen)
3. Decompile SystemUIGoogle.apk or SystemUI.apk (Tickle My Android)
4. Apply all xml changes (use Notepad++ and/or WinMerge) from "res" folder
5. Recompile the modified SystemUIGoogle.apk or SystemUI.apk just with the xml changes
6. Decompile that modified SystemUIGoogle.apk or SystemUI.apk (we need this to obtain the new public.xml where all new IDs will be present)
7. Apply all smali changes (Notepad++ | WinMerge)
   - Remember that ALL IDs will probably be DIFFERENT, YOU MUST change them accordingly to your ROM
   - Also, you must check on smali files for every 0x10xxxx or 0x11xxxx instances. They are context resources values and could be different. You'll need to change them accordingly as well.
8. Recompile the fully modified SystemUIGoogle.apk or SystemUI.apk (Tickle My Android)
9. Take res folder, resources.arsc and all classes.dex from recompiled SystemUIGoogle.apk and store them inside SystemUIGoogle.apk VR Theme zip folder. If you're using a SystemUI.apk, rename the folder inside the zip to SystemUI.apk.
10. Flash VR Theme zip through Magisk
11. Reboot your device to check if it's booting ok after all changes
   - If not, take logcats at boot to check what went wrong
12. If it rebooted ok, then you can use RomControl app to MOD your device.
13. RomControl is already compiled on XDA Thread inside the "Addon Features" Magisk Module Zip. You can use it as it is, or download the code and compile it yourselt. Make every change you want/need, I don't care. Remember that Rom Control needs to be installed on /system/priv-app, if not, it won't be allowed to store system settings.
