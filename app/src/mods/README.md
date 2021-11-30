**What is this?**



MODs_12 zip is a complilation of all xml and smali changes made in order to get custom features working on Pixel devices.

You can download this zip, extract it, and use it to implement the changes on your own device as long as it's running an AOSP based Android 12 ROM.




**Tools you'll need:**


- SuperR's Kitchen (to extract system image from your ROM zip and deodex it, if needed)

https://forum.xda-developers.com/t/windows-linux-mac-donate-superrs-kitchen-v3-2-1-2-1-14-2021.3601702/


- Tickle My Android (to decompile/recompile framework-res.apk and SystemUIGoogle.apk or SystemUI.apk, if using other ROM rather than stock Pixel)

https://forum.xda-developers.com/t/tool-tickle-my-android-decompile-recompile-with-ease.1633333/


- Magisk

https://forum.xda-developers.com/t/magisk-the-magic-mask-for-android.3473445/


- VR Theme zip (included in this repository).

- Notepad++

https://notepad-plus-plus.org/downloads/

- WinMerge

https://winmerge.org/




I'm assuming you know what to do with this. Things are already made easy so I'm not going to guide you step by step...well...I will guide you though...




**How-to:**

1. Extract your desired ROM zip (using Kitchen)
2. Fully deodex the ROM if needed (using Kitchen)
3. Decompile farmework-res.apk and SystemUIGoogle.apk or SystemUI.apk (Tickle My Android)
4. Apply all xml changes (use Notepad++ and/or WinMerge) from "res" folders
5. Recompile the modified SystemUIGoogle.apk or SystemUI.apk just with the xml changes
6. Decompile that modified SystemUIGoogle.apk or SystemUI.apk (we need this to obtain the new public.xml where all new IDs will be present)
7. Apply all smali changes (Notepad++ | WinMerge)
   - Remember that ALL IDs will probably be DIFFERENT, YOU MUST change them accordingly to your ROM
   - Also, you must check on smali files for every 0x10xxxx or 0x11xxxx instances. They are context resources values and could be different. You'll need to change them accordingly as well.
8. Recompile the fully modified framework-res.apk and SystemUIGoogle.apk or SystemUI.apk (Tickle My Android)
9. Take res folder, resources.arsc and all classes.dex from recompiled framework-res.apk and SystemUIGoogle.apk or SystemUI.apk and store them inside SystemUIGoogle.apk VR Theme zip folder. If you're using a SystemUI.apk, rename the folder inside the zip to SystemUI.apk.
10. Flash VR Theme zip through Magisk
11. Reboot your device to check if it's booting ok after all changes
   - If not, take logcats at boot to check what went wrong
12. If it rebooted ok, then you can use RomControl app to MOD your device.
13. RomControl is already compiled on XDA Thread inside the "Addon Features" Magisk Module Zip. You can use it as it is, or download the code and compile it yourself. Make every change you want/need, I don't care. Remember that Rom Control needs to be installed on /system/priv-app, if not, it won't be allowed to store system settings.
