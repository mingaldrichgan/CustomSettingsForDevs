**What is this?**



MODs_AOSP zip is a complilation of all xml and smali changes made in order to get custom features working on Xiaomi Mi A3.

You can download this zip, extract it, and use it to implement the changes on your own device ROM, as long as it's running an AOSP (or close to it) Android 11 ROM.




**Tools you'll need:**


- SuperR's Kitchen (to extract system image from your stock ROM zip and deodex your ROM, if needed)

https://forum.xda-developers.com/t/windows-linux-mac-donate-superrs-kitchen-v3-2-1-2-1-14-2021.3601702/


- Tickle My Android (to decompile/recompile SystemUIGoogle.apk or SystemUI.apk)

https://forum.xda-developers.com/t/tool-tickle-my-android-decompile-recompile-with-ease.1633333/


- Magisk

https://forum.xda-developers.com/t/magisk-the-magic-mask-for-android.3473445/


- VR Theme zip (included in this repository). Use Pixel version if using a Pixel phone.

- Notepad++

- WinMerge




I'm assuming you know what to do with this. Things are already made easy so I'm not going to guide you step by step.




**How-to:**

1. Extract your desired ROM zip (using Kitchen)
2. Fully deodex the ROM if needed (using Kitchen)
3. Decompile SystemUI.apk (Tickle My Android)
4. Apply all xml and smali changes (Notepad++ | WinMerge)
   - Remember that ALL IDs are DIFFERENT, YOU MUST change them accordingly to your ROM
   - Also, you must check on smali files for every 0x10xxxx or 0x11xxxx instances. They are context resources and could be different. You'll need to change them accordingly as well.
5. Recompile SystemUI.apk (Tickle My Android)
6. Take res folder, resources.arsc and all classes.dex from recompiled SystemUI.apk and store them inside SystemUI.apk or SytemUIGoogle.apk VR Theme zip folder.
7. Flash VR Theme zip through Magisk
8. Reboot your device to check if it's booting ok after all changes
   - If not, take logcats at boot to check what went wrong
9. If it rebooted ok, then you can use RomControl app to MOD your device.
10. RomControl is already compiled on XDA Thread inside the Magisk Module Zip for Pixel4a. You can use it as it is, or download the code and compile it yourselt. Make every change you want/need, I don't care. Remember that Rom Control needs to be installed on /system/priv-app, if not, it won't be allowed to store system settings.
