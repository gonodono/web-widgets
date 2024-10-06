# Web Widgets

Basic examples of putting rendered `WebView` content into App Widget images
along with various features, for both `RemoteViews` and Glance.

<p align="center">
<img src="images/widgets.png"
alt="Screenshots of emulators showing an instance of each non-Minimal Widget."
width="30%" />
</p>

This project contains complete working examples to supplement those outlined in
[this Stack Overflow post][so-post]. There might be relevant information or
details there that I may have overlooked here, and vice versa.

## Contents

- [Overview](#overview)
- [RemoteViews](#remoteviews)
- [Glance](#glance)
- [Notes](#notes)

## Overview

- Each framework has three versions: Minimal, Simple, and Scroll.

  - The Minimal ones are rather bare-bones; just enough to show as busy before
    displaying a static image or a failure message.

  - The Simple versions provide reload buttons, create more efficient images,
    and are clickable to allow opening their pages in a browser app.

  - The Scroll ones have the same features as the Simple, but the image is
    created to be (almost) as tall as is allowed by the App Widget API's size
    limit, and is then displayed as the only item in a `ListView` or
    `LazyColumn`.

- All of the examples use the same overall method to generate their images: a
  `WebView` is instantiated and, in order to enable rendering, attached to
  `WindowManager` inside a zero-by-zero `FrameLayout`. A page is then loaded,
  laid out, and drawn to a `Bitmap` that's displayed in the Widget, after which
  everything is cleaned up. For the non-Minimal ones, this is all consolidated
  into a single helper class named [`WebShooter`][web-shooter].

- Neither framework's examples really do much as far as data persistence goes.
  The Glance versions manage to use only runtime variables, because
  `GlanceAppWidget`s hang around until the process is killed. The `RemoteViews`
  versions do save state to disk, but only because `AppWidgetProvider` instances
  are short-lived, and a new one is created for each Widget action.

- All of the examples assume that the page will load relatively quickly. Each
  one uses only the time available to it from its own component; i.e., there are
  no separate `Worker`s or loader `Service`s. Consult the corresponding sections
  below for the individual examples' respective time limits. In production, I'd
  suggest using `Worker`s everywhere. [This official Glance sample][sample] has
  an `ImageWorker` that's very close to what would be needed here.

- All of the examples use Wikipedia for their pages. I am not affiliated with
  The Wikimedia Foundation nor any of its sites or organizations. It is simply a
  reliable, lightweight site with a random page functionality. The reproductions
  of small sections of various Wikipedia articles used in this document's
  graphics are believed to constitute fair use.

## RemoteViews

To keep things short and clear, the identifiers for packages and classes in this
framework use `View` instead of `RemoteViews`; e.g., `ViewSimpleWidgetProvider`.

### Minimal

<sup>[`ViewMinimalWidgetProvider`][view-minimal]</sup>

The Minimal version simply displays a static image or a failure message after
showing an indeterminate indicator while busy. The actual image is the same
width as the screen and half its height, though it's obviously scaled down in
the Widget. There's nothing special about the height measure; I cut it in half
so it kinda fits OK in a 2x2 portrait Widget.

A coroutine is launched from the `AppWidgetProvider`'s `onUpdate()` to handle
the page load, layout, and draw. It takes advantage of `BroadcastReceiver`'s
`goAsync()` functionality to get about 10 seconds to finish its work.

### Simple

<sup>[`ViewSimpleWidgetProvider`][view-simple]</sup>

The Simple one adds a reload button to force a new random page, and the image
itself can be clicked to open the currently displayed page in a (separate)
browser app. Also, this one creates `Bitmap`s that are sized to match the Widget
rather than the screen, to cut down on overhead.

This one launches a coroutine from `onUpdate()` like the Minimal one, with the
same ~10-second timeout, but its `WebView` operations and draw routine are all
contained in the [`WebShooter`][web-shooter] class.

### Scroll

<sup>[`ViewScrollWidgetProvider`][view-scroll]</sup>

The Scroll version basically adds scrolling to the Simple one, though it's a bit
more complicated than it sounds. The only scrolling containers allowed in
`RemoteViews` are a handful of `AdapterView`s, so this one requires a separate
`RemoteViewsService` and `RemoteViewsFactory`, too.

The `WebShooter` work is handled in the `RemoteViewsFactory`, so there's plenty
of time available, but it's capped at 40 seconds here to match the timeout for
the Glance Widgets.

Because of the unique setup here, this one ends up with a slightly different UI
if it errors or times out, as those messages are displayed in `ListView` items.
Also, since the reload button is handled in the `Provider` but the `WebShooter`
runs in the `Factory`, there's no easy way to disable that button if the shooter
figures out it can't draw. This one is mainly demonstrating how to use the time
available in the `Factory`, if that might be useful for your particular design,
so I didn't go to too much trouble to ensure feature parity here.

## Glance

Each Glance Widget has the same behavior and features as the corresponding
`RemoteViews` version, apart from the small UI difference for errors/timeouts
in Scroll. They all have the same timeout of 40 seconds, to come in under the
documentation's stated limit of "about 45 seconds" for `provideContent()`.

### Minimal

<sup>[`GlanceMinimalWidget`][glance-minimal]</sup>

The Minimal one again handles the load, layout, and draw internally, showing the
same busy indicator and static results.

### Simple

<sup>[`BaseGlanceWidget`][glance-base],
[`GlanceSimpleWidget`][glance-simple]</sup>

Thanks to Glance's abstractions, the Simple and Scroll versions are nearly
identical. Both inherit from `BaseGlanceWidget`, which handles all of the
[`WebShooter`][web-shooter] work. `GlanceSimpleWidget` just tells the base class
that the image should fit the Widget's height, and then provides a static image
`Composable`.

### Scroll

<sup>[`BaseGlanceWidget`][glance-base],
[`GlanceScrollWidget`][glance-scroll]</sup>

This one tells `BaseGlanceWidget` that the image height should be as tall as
possible, and provides a `LazyColumn` with a single item for the image.

## Notes

The `WebView` in all of these examples uses default settings; i.e., no
JavaScript, no web storage, etc. The given routine seems to work quite robustly
with those defaults, at least on the emulators, all the way back to Nougat. I
haven't done much testing with any other configurations, but it seems that
enabling certain settings might cause `WebView` to need a few more frames before
everything's drawable (not counting whatever might be required by any
long-running scripts).

The [platform CTS helper class][helper] that was consulted for the
`postVisualStateCallback()` setup adds a `ViewTreeObserver.OnDrawListener` upon
the visual callback, and then invalidates to cause an extra frame before drawing
in order to ensure it's ready. Something similar is possible with this solution,
with a few adjustments, but it wasn't necessary for my basic examples.

If you do find that your setup isn't fully prepared before the draw, it might be
simpler to add a short `delay()` after the `awaitLayout()`, instead of trying to
figure out an exact number of frames to wait for.

<br />

## License

MIT License

Copyright (c) 2024 Mike M.

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.


  [so-post]: https://stackoverflow.com/a/33981965

  [web-shooter]: https://github.com/gonodono/web-widgets/blob/main/app/src/main/kotlin/com/gonodono/webwidgets/WebShooter.kt

  [sample]: https://github.com/android/user-interface-samples/tree/main/AppWidget/app/src/main/java/com/example/android/appwidget/glance/image

  [view-minimal]: https://github.com/gonodono/web-widgets/blob/main/app/src/main/kotlin/com/gonodono/webwidgets/view/minimal/ViewMinimalWidgetProvider.kt

  [view-simple]: https://github.com/gonodono/web-widgets/blob/main/app/src/main/kotlin/com/gonodono/webwidgets/view/simple/ViewSimpleWidgetProvider.kt

  [view-scroll]: https://github.com/gonodono/web-widgets/blob/main/app/src/main/kotlin/com/gonodono/webwidgets/view/scroll/ViewScrollWidgetProvider.kt

  [glance-minimal]: https://github.com/gonodono/web-widgets/blob/main/app/src/main/kotlin/com/gonodono/webwidgets/glance/minimal/GlanceMinimalWidget.kt

  [glance-base]: https://github.com/gonodono/web-widgets/blob/main/app/src/main/kotlin/com/gonodono/webwidgets/glance/BaseGlanceWidget.kt

  [glance-simple]: https://github.com/gonodono/web-widgets/blob/main/app/src/main/kotlin/com/gonodono/webwidgets/glance/simple/GlanceSimpleWidget.kt

  [glance-scroll]: https://github.com/gonodono/web-widgets/blob/main/app/src/main/kotlin/com/gonodono/webwidgets/glance/scroll/GlanceScrollWidget.kt

  [helper]: https://cs.android.com/android/platform/superproject/main/+/main:cts/tests/tests/uirendering/src/android/uirendering/cts/util/WebViewReadyHelper.java