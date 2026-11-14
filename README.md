# blog.sh for Windows and Linux

`./blog.sh --anywhere` on a desktop: the whole of `./blog.sh` in a
window, on Windows and on Linux, each on x86_64 and on arm64. A new post
with photographs, a post's text in every language the site publishes,
its properties and the actions on it, the scheduled-post queue, the
archive with its filters and search, the trash, and the site's rebuild.

It is the [Android app](https://github.com/DanielSnor/blog.sh-android)
as its tablet held on its side shows it: the menu, and beside it the
screen that is open. The same screens, the same words, the same commands
to the engine. What differs on a desktop is said below, under *Where it
is not the Android app*. A Mac needs none of it -- it runs the
[iOS app](https://github.com/DanielSnor/blog.sh-ios) as it is.

It is a thin client. Nothing of the engine is reimplemented: the app
opens an SSH connection to the machine the blog lives on and runs
`./blog.sh <command> --json` there, the same commands a person types,
and draws the answers. The archive, the build and the deploy stay where
they are.

Requires [blog.sh](https://github.com/DanielSnor/blog.sh) 1.10 or later
on the server.

**Where it stands:** 0.1, the first version to be tried by somebody
other than the person who wrote it. Everything on the screens works as
it does on an Android tablet, where the Android app has been in use for
weeks; what is new is the desktop around them -- the window, the keys,
the files, the installers. That is what is to be tried.

## Installing it

### Linux

A `.deb`, for Debian, Ubuntu and what is built on them (Mint, Pop!_OS,
elementary, …). Take the one for your machine -- `dpkg
--print-architecture` says which:

| `dpkg --print-architecture` | package |
|---|---|
| `amd64` | `blogsh_0.1.0_amd64.deb` |
| `arm64` | `blogsh_0.1.0_arm64.deb` |

```
sudo apt install ./blogsh_0.1.0_amd64.deb
```

The `./` matters: without it apt looks for a package of that name in its
repositories. The app goes to `/opt/blogsh`, carrying the Java it runs
on -- nothing else is installed, and no Java of the system's is used. It
is in the menu of applications as **blogsh**, among the office
programs; from a terminal it is

```
/opt/blogsh/bin/blogsh
```

which is also the way to start it when something goes wrong: whatever
it has to say is printed there.

On a system without a desktop's menu (a server, a minimal install) the
installation stops at *No writable system menu directory found*. The
app itself is in place; `sudo mkdir -p /etc/xdg/menus && sudo dpkg
--configure blogsh` finishes it.

### Windows

A zip, for now -- no installer yet: `blogsh-0.1.0-windows-x64.zip` for
an ordinary PC, `blogsh-0.1.0-windows-arm64.zip` for a Windows on ARM
(Surface Pro X and later, Snapdragon laptops; *Settings → System →
About* says *ARM-based processor*).

1. Unpack it anywhere -- the Desktop will do. It makes a folder
   `blogsh`.
2. Run `blogsh.cmd` in it.

Windows may stop a program that came from the internet and is signed by
nobody it knows: *More info → Run anyway*, or, before unpacking, the
zip's *Properties → Unblock*. Nothing is installed; the folder is the
app, and deleting it removes it.

## Connecting a blog

There are three ways in, all on the first screen while there is no blog
yet, and later behind the blog's name at the top of the menu (*Add a
blog*).

**With a code.** On the server, in the blog's directory:

```
./blog.sh pair
```

It shows a code -- a picture, and under it the same thing as one line of
text starting `blogsh://pair?`. A desktop has no camera to read the
picture: copy the line, and in the app paste it into the field under
*with a code* (Ctrl+V, or the *Paste* key under the field). The app asks
*Connect to …?*, says which machine and account, and *connect* lets it
in: nobody types an address, an account or a key. The code is good for
ten minutes and once.

The code says where the app is to connect. Where `pair` guesses the
address wrongly -- a server behind a router, a name the desktop does not
know -- tell it: `./blog.sh pair --host blog.example.com --port 22`.

The server writes this computer down under its name (the one `hostname`
says); `./blog.sh pair --list` shows it there, and `./blog.sh pair
--revoke <name>` takes it out again.

**Without a server.** *Try the practice blog*: a blog that lives in the
app -- seven posts to look around in, every screen there to open.
Nothing can be written to it, and nothing is sent anywhere. The way to
see the app before connecting anything.

**By hand**, for a blog that lives in a container, or a server whose
`~/.ssh/authorized_keys` somebody keeps themselves: *Set up by hand*
asks for the server's host, the account and the port, makes the app's
key, and from the blog's directory on the server writes the line for
`authorized_keys`, with the forced command in front of the key that is
all it may ever run. *Copy the authorized_keys line* puts it on the
clipboard; on the server it goes into the account's
`~/.ssh/authorized_keys`, and *test the connection* says whether the
blog answers.

## What to try

A first round, an hour or so with a blog of your own -- or a copy of
one: everything the app does, it does to the blog.

1. **Install and start it.** It opens on the first screen, in the
   system's language if that is Czech or German, in English otherwise
   (the language can be chosen under the gear).
2. **Connect a blog with a code.** The first screen fills: the blog's
   name, its drafts, its queue, the numbers under the search.
3. **Walk the menu.** Each tile opens its screen beside the menu: a new
   post, a post, the queue, the archive, the trash, the site. **Esc** is
   the way back -- out of a sheet first, then the screen beside the
   menu. The key at the top left puts the menu away and brings it back.
4. **Write a post with a picture**, and send it as a draft. *Add a
   picture or video* opens the system's own dialog for files. Look at
   the preview before sending: it opens in your browser.
5. **Change the post**: its text, a property, its place in the queue.
   Publish it, unpublish it, delete it and restore it from the trash --
   the app rebuilds the site after each by itself.
6. **The window.** Make it larger, smaller (it goes down to 900 × 600),
   put it on another screen. Larger type is under the gear.
7. **Without a network.** Pull the cable or turn off the Wi-Fi while
   the app is open: the blog is said to be out of reach, and a new post
   is kept on the computer and goes by itself when the server answers
   again.
8. **Close the app and start it again.** The blog is still there, and
   so is a post that was being written.

Whatever looks wrong, or reads oddly, or does something you did not
expect, is worth a line -- see the end of this file.

## Where it is not the Android app

| on an Android tablet | here |
|---|---|
| the camera reads the pairing code | the code's line of text is pasted |
| the photo library, and the device's files | the system's dialog for files -- *Add a picture or video* and *Add from files* both open it |
| a video is converted to H.264 at 720p before it goes | a video goes as it is, and has to fit into what the blog takes |
| the preview of a post is drawn in the app | it opens in the browser |
| a video in the viewer is played in the app | it opens in the system's player |
| the share sheet | the post's address goes to the clipboard, and the app says so |
| the system's way back | Esc |

The colours, the type, the marks and every word are the Android app's.

## What it keeps, and taking it away

Everything the app keeps is in one directory of its own:

| | |
|---|---|
| Linux | `~/.local/share/blogsh` (or `$XDG_DATA_HOME/blogsh`) |
| Windows | `%APPDATA%\blogsh` |

In it, `settings.properties` (the blogs, the server's key each one
trusts, the language), `keys/` (each blog's key), and what was written
and not sent yet.

Each blog has a key the app makes itself and never lets out. On
Windows its seed is sealed by the system (DPAPI) to the user who is
signed in: another user of the computer, or a copy of the file taken
elsewhere, opens nothing. On Linux it is a file only its owner may read
(`0600` in a `0700` directory), as `ssh-keygen` leaves a key without a
passphrase -- the system's keyring is not used yet.

To take the app away:

- Linux: `sudo apt remove blogsh`, and `rm -r ~/.local/share/blogsh` for
  what it kept.
- Windows: delete the `blogsh` folder, and `%APPDATA%\blogsh`.
- On the server: `./blog.sh pair --revoke <this computer's name>`, or
  the line from `~/.ssh/authorized_keys` for a blog set up by hand.

## Reporting what you found

As an [issue on this repository](https://github.com/DanielSnor/blog.sh-desktop/issues),
or to whoever gave you the package. What helps:

- the system: the distribution and its version, or the Windows edition;
  x86_64 or arm64; on Linux the desktop (GNOME, KDE, …) and whether it
  runs on Wayland or X11 (`echo $XDG_SESSION_TYPE`);
- the engine's version on the server (the line under the blog's name in
  the menu);
- what you did, what happened, and what you expected instead;
- a screenshot, where it is something to see;
- on Linux, what the terminal said when the app was started from it
  (`/opt/blogsh/bin/blogsh`).

## What is here

- `src/` -- what only a desktop has: where the app's files go, how its
  keys are kept, the window, and its own answers to what the screens ask
  of the system (`OnDesktop.kt`).
- `shared/` -- what it has in common with the Android app: the model
  (the engine's answers, the blogs, the connection), the screens, the
  words in every language, the marks and the faces of type. Copied from
  the Android app and never edited here; `shared/README.md` says how it
  is taken over and `shared/SOURCE` from which commit.

Nothing outside this repository is needed to build it.

## Building

Java 17 or later (21 recommended); `JAVA_HOME` names it.

- `./gradlew run` -- the app
- `./gradlew test` -- its tests, and every screen drawn as a picture into
  the build (`screens/`)
- `./gradlew packageMsi` on Windows, `./gradlew packageDeb` on Linux --
  the installer for the system it is built on

An installer is made by the Java of the system it is for, on it. For
another system, `-Pblogsh.target=linux-x64` (or `linux-arm64`,
`windows-x64`, `windows-arm64`) builds the app for it, and
`./gradlew appJars` puts the app and its libraries into
`build/app-jars`, for that system's `jpackage` to make the installer of;
`./gradlew printRuntimeModules` says which parts of Java it has to carry.

On the Mac it is written on, `tools/gw` is the wrapper with Android
Studio's own Java, and `local.properties` (`build.root=…`) keeps the
build out of the folder iCloud syncs.

## License

MIT, see [LICENSE](LICENSE).
