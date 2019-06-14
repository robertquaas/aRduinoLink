if you have problems connecting to remote device over adb for debugging try:
1: make sure developer options are enabled on remote device
	in settings->about device->click build number 7 times
		click back, go into the new option developer options
			toggle ADB debugging
			revoke ADB debugging authorizations
2: on dev machine terminal through android studio:
	adb kill-server
	adb start-server 
3: on dev machine through terminal again:
	adb connect 192.168.*.*
		//ip of remote machine
	if connection refused try different ports:
		adb connect 192.168.*.*:5555
		adb connect 192.168.*.*:22
4. generate new adbkeys on dev machine
	in ~/.android
		rm adbkey
		rm adbkey.pub
		adb keygen .android/adbkey
	check to make sure keys are there
5. if that doesn't work check stackoverflow ;)
