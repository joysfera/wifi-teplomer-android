# WiFi Teploměr pro Android

This is my application for Android that supports [WiFi thermometer project](https://teploty.info/ "Teploty.info").

It's incomplete and unpolished, hacked together from various examples and generally below my normal coding standards.

I'm releasing it as-is with the hope that interested parties could provide pull requests with improvements.

Creating an Android widget that works in the background and refreshes automagically its content by downloading data from Internet turned out to be a daunting task because nowadays various Android OEM flavors like to kill each task that does not run in foreground, which unfortunately includes also all widgets. Please see the web [Don't kill my app!](https://dontkillmyapp.com/) to see how bad the situation for programmers and users is.

I tried working around this issue on some phones by various hacks in the source code - git log can show you what was tried and what helped or didn't help. Every Android version and every OEM modified Android is different, unfortunately. Getting the widget working reliably is rather heroic task.

Enjoy

petr@pstehlik.cz
