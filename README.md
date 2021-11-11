# AudLibPlayer

AudLibPlayer is a library that allows a client to play from a collection of public domain audiobooks hosted on a web service. It supports the following actions:
- playing
- stopping
- pausing
- resuming
- seeking
- progress reporting

Binding to the service edu.temple.audlibplayer.PlayerService, found in this library, returns an instance of PlayerService.MediaControlBinder, which implements the following functions:
- **play(id: Int)**: Streams the audiobook for the provided ID via an internet connection
- **play(file: File)**: Begins playing the specified file
- **play(file: File, position: Int)**: Begins playing the specified file from the specified position in seconds
- **pause()**: Pauses the currently playing audiobook, or plays the audiobook if paused
- **stop()**: Stops the curently playing audiobook
- **setProgressHandler(progressHandler: Handler)**: Accepts a handler that will be used to provide progress updates. Message.obj will contain a PlayerService.BookProgress object representing the progress of the currently playing book thus far in seconds
  * int BookProgress.bookId is the ID of the currently playing book (if one is available)
  * int BookProgress.bookUri is the file Uri of the currently playing book (if one is available)
  * int BookProgress.progress is the progress of the currently playing book
- **seekTo(position: Int)**: Jumps to specified position in the current audiobook. Does not interrupt playback
- **isPlaying(): Boolean**: Reports whether or not an audiobook is currently being played

Download the library [here](https://kamorris.com/lab/audlib/audlib-player.aar)

To add it to your project:
1. copy the aar file to your project's **app/libs** folder
2. add the line *implementation files('libs/audlib-player.aar')* to your app module's dependencies
