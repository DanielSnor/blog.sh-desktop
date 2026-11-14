# blog.sh for Windows

`./blog.sh --anywhere` on a desktop: Windows first, and Linux from the
same code. A Mac needs none of it -- it runs the
[iOS app](https://github.com/DanielSnor/blog.sh-ios) as it is.

Like the phone apps it is a thin client. Nothing of the engine is
reimplemented: the app opens an SSH connection to the machine the blog
lives on and runs `./blog.sh <command> --json` there, and draws the
answers.

It is not a third copy of the app. The model -- the engine's answers,
the blogs, the connection, what is kept on the device -- is the
[Android app's](https://github.com/DanielSnor/blog.sh-android), which
keeps it as a build of its own (`core/`); this app includes that build
and adds what only a desktop has: where its files go, how its keys are
kept, a window.

**Where it stands:** a beginning. A blog is connected by the code
`./blog.sh pair` shows, given as its line of text, and its first screen
is read: who the blog is, its queue and its drafts.

## Keys

Each blog has a key the app makes itself and never lets out. On
Windows its seed is sealed by the system (DPAPI) to the user who is
signed in, under `%APPDATA%\blogsh\keys`: another user of the computer,
or a copy of the file taken elsewhere, opens nothing. Elsewhere it is a
file only its owner may read, as `ssh-keygen` leaves a key without a
passphrase.

## Building

The Android app's working copy has to lie beside this one:

```
blog.sh-android/
blog.sh-desktop/
```

`tools/gw` is the Gradle wrapper with Android Studio's own Java when no
other is named:

- `tools/gw run` -- the app
- `tools/gw test` -- its tests (the model's are run by the Android app's)
- `tools/gw packageMsi` -- the Windows installer; built on Windows only

The words are the Android app's own files, carried in at build time:
the two apps say the same things the same way.

## License

MIT, see [LICENSE](LICENSE).
