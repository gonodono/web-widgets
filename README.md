# Web Widgets

Basic examples of putting rendered `WebView` content into an App Widget
image along with various features, for both `RemoteViews` and Glance.

<p align="center">
<img src="images/widgets.png"
alt="Screenshot of an emulator with an instance of each non-Minimal Widget."
width="30%" />
</p>

This project contains complete working examples to supplement those outlined in
[this Stack Overflow post][so-post]. There might be relevant info or details
there that I may have overlooked here, and vice versa.


## Overview

+ All of the examples use the same overall method to generate their images. A
  `WebView` is instantiated and, in order to enable rendering, attached to
  `WindowManager` inside a zero-by-zero `FrameLayout`. A page is then loaded,
  laid out, and drawn to a `Bitmap` that's displayed in the Widget, after which
  everything is cleaned up. For the non-Minimal ones, this is all consolidated
  into a single helper class named `WebShooter`.

+ Each framework has three versions: Minimal, Simple, and Scroll.

  + The Minimal ones are rather bare-bones; just enough to show as busy before
    displaying a static image or an error.

  + The Simple versions provide reload buttons, create more efficient images,
    and are clickable to allow opening their pages in a browser app.

  + The Scroll ones have the same features as the Simple, but the image is
    created to be as tall as is allowed by the App Widget API's size limit, and
    is then displayed as the only item in a `ListView` or `LazyColumn`.

+ Neither framework's examples really do much as far as data persistence goes.
  The Glance versions manage to use only runtime variables, because
  `GlanceAppWidget`s hang around until the process is killed. The `RemoteViews`
  versions do save state to disk, but only because `AppWidgetProvider` instances
  are short-lived, and a new one is created for each Widget action.

+ All of the examples assume that the page will load relatively quickly. Each
  one uses only the time available to it from its own component; i.e., there are
  no separate `Worker`s or loader `Service`s. Consult the corresponding sections
  below for the individual examples' respective time limits. In production, I'd
  suggest using `Worker`s everywhere. [This official Glance sample][sample] has
  an `ImageWorker` that's very close to what would be needed here.

+ All of the examples use Wikipedia for their pages. I am not affiliated with
  The Wikimedia Foundation, nor any of its sites or organizations. It is simply
  a reliable, lightweight site with a random page functionality. The
  reproductions of small sections of various Wikipedia articles used in this
  document's graphics are believed to constitute fair use.


## RemoteViews

### Minimal

The Minimal version simply displays a static image or an error message after
showing an indeterminate indicator while busy. The actual image is the same
width as the screen and half its height, though it's obviously scaled down in the
Widget. There's nothing special about the height measure; I cut it in half so it
kinda fits OK in a 2x2 portrait Widget.

A coroutine is launched from the `AppWidgetProvider`'s `onUpdate()` to handle
the page load, layout, and draw. It takes advantage of `BroadcastReceiver`'s
`goAsync()` functionality to get about 10 seconds to finish its work.

### Simple

The Simple one adds a reload button to force a new random page, and the image
itself can be clicked to open the currently displayed page in a (separate)
browser app. Also, this one creates `Bitmap`s that are sized to match the Widget
rather than the screen, to cut down on overhead.

This one launches a coroutine from `onUpdate()` just like the Minimal, with the
same 10-second timeout, but its `WebView` operations and draw routine are all
contained in the `WebShooter` class.

### Scroll

The Scroll version basically adds scrolling to the Simple one, though it's a bit
more complicated than it sounds. The only scrolling containers allowed in
`RemoteViews` are a handful of `AdapterView`s, so this one requires a separate
`RemoteViewsService` and `RemoteViewsFactory`, too.

The `WebShooter` work is handled in the `RemoteViewsFactory`, so there's plenty
of time available, but it's capped at 40 seconds here to match the timeout for
the Glance Widgets.


## Glance

Each Glance Widget has the same behavior and features as the corresponding
`RemoteViews` version. The Minimal one again uses the individual helper
functions directly, while the Simple and Scroll versions use `WebShooter` to
handle the load, layout, and draw. The Simple and Scroll ones are nearly
identical, actually, and both inherit from a base class that handles most of the
work.

These Widgets all have the same timeout of 40 seconds, to come in under the
documentation's stated limit of "about 45 seconds" for `provideContent()`.

Apart from the stuff that Glance stores internally, none of these Widgets
persist anything to disk, since `GlanceAppWidget` instances hang around holding
state long enough to suffice for these basic examples.

<br />

## License

MIT License

Copyright (c) 2024 Mike M.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.


  [sample]: https://github.com/android/user-interface-samples/tree/main/AppWidget/app/src/main/java/com/example/android/appwidget/glance/image

  [so-post]: https://stackoverflow.com/a/33981965