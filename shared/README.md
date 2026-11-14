# shared/

What this app has in common with the
[Android app](https://github.com/DanielSnor/blog.sh-android), copied from
it: the model (the engine's answers, the blogs, the connection, what is
kept on the device), the screens, the words in every language, the marks
and the faces of type, and the answers the pictures of the screens are
drawn from in the tests. `SOURCE` says which commit of the Android app
the copy is.

Nothing here is edited in this repository. A change to a screen or to
the model is made in the Android app, and taken over with

```
tools/sync-android [path to blog.sh-android]
```

which replaces this directory with what that commit has. What only a
desktop has -- where its files go, how its keys are kept, the window, and
its own answers to what the screens ask of the system -- is in `src/`.
