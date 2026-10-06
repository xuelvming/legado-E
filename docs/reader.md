# Reader: from chapter content to an interactive page

[Architecture overview](README.md) | [Content sources](content-sources.md) |
[Data and sync](data-and-sync.md)

This guide follows the **text reader**. Manga, source audio/video and RSS have
their own presentation paths; see [Integrations](integrations.md).

## 1. Who owns what?

```mermaid
flowchart TB
    Activity["ReadBookActivity: screen, menus and callbacks"]
    VM["ReadBookViewModel: initialization and book operations"]
    Model["ReadBook: shared reading-session state"]
    Content["BookHelp, CacheBook and content providers"]
    Processor["ContentProcessor"]
    Layout["ChapterProvider and TextChapterLayout"]
    Pages["TextChapter -> TextPage -> TextLine / TextColumn"]
    Views["ReadView -> PageView -> ContentTextView"]
    Activity <--> VM
    VM --> Model
    Model <--> Content
    Model --> Processor --> Layout --> Pages
    Model -->|"Page availability callbacks"| Activity
    Activity --> Views
    Pages --> Views
    Views -->|"Navigation and selection callbacks"| Activity
    Activity --> Model
```

| Component | Responsibility |
| --- | --- |
| [ReadBookActivity](../app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt) | Screen lifecycle, controls, configuration, callbacks and text actions |
| [ReadBookViewModel](../app/src/main/java/io/legado/app/ui/book/read/ReadBookViewModel.kt) | Resolve the requested book, load metadata/TOC, change sources and coordinate sync |
| [ReadBook](../app/src/main/java/io/legado/app/model/ReadBook.kt) | Current book/source, chapter and position, neighboring chapters, loading and progress |
| [ContentProcessor](../app/src/main/java/io/legado/app/help/book/ContentProcessor.kt) | Display-time replacements, title handling and configured Chinese conversion |
| [ChapterProvider](../app/src/main/java/io/legado/app/ui/book/read/page/provider/ChapterProvider.kt) | Fonts, paints, available dimensions and creation of laid-out chapters |
| [TextChapterLayout](../app/src/main/java/io/legado/app/ui/book/read/page/provider/TextChapterLayout.kt) | Turn chapter content into pages and lines, including image/HTML-aware cases |
| [Reader views](../app/src/main/java/io/legado/app/ui/book/read/page) | Draw pages, interpret gestures and coordinate page animations |

`ReadBook` is an application-level Kotlin object, not a screen-local ViewModel.
Services such as read-aloud also use it. Lifecycle, cancellation and callback
registration matter when changing the reader.

## 2. Opening a book and obtaining content

The ViewModel resolves a book from the intent or last-read state, initializes
`ReadBook`, checks local-file access when applicable and ensures chapters exist.
Online details/TOC come from `WebBook`; local chapters come from `LocalBook`.
Missing data and permission failures have UI/error paths.

The following shows one successful text-chapter load after initialization:

```mermaid
sequenceDiagram
    participant VM as ReadBookViewModel
    participant Read as ReadBook
    participant DB as Room chapter DAO
    participant Help as BookHelp
    participant Local as LocalBook
    participant Cache as CacheBook
    participant Web as WebBook and BookContent
    participant Layout as ContentProcessor and ChapterProvider
    participant UI as ReadBookActivity and views
    VM->>Read: loadContent
    Read->>DB: Find chapter by book URL and index
    DB-->>Read: BookChapter
    Read->>Help: getContent(book, chapter)
    alt Cached chapter file exists
        Help-->>Read: Cached text
    else Local book
        Help->>Local: getContent(book, chapter)
        Local-->>Help: Format-specific content
        Help-->>Read: Content
    else Online cache miss
        Help-->>Read: No cached content
        Read->>Cache: Download chapter
        Cache->>Web: getContentAwait
        Web->>Web: Request, extract and combine content pages
        Web->>Help: Save content when requested
        Web-->>Cache: Content
        Cache-->>Read: contentLoadFinish callback
    end
    Read->>Layout: Process text and start asynchronous layout
    loop Pages become available
        Layout-->>Read: Laid-out page
        Read-->>UI: Update content and page availability
    end
```

[BookHelp.getContent](../app/src/main/java/io/legado/app/help/book/BookHelp.kt)
checks the chapter file cache before calling the local parser.
[CacheBook](../app/src/main/java/io/legado/app/model/CacheBook.kt) coordinates
online downloads. Bulk offline caching also uses it through
[CacheBookService](../app/src/main/java/io/legado/app/service/CacheBookService.kt).

The reader loads the current, next and previous chapters. Configured
pre-download can fetch additional chapters without laying out all of them.
Network download, file caching and layout are separate operations.

## 3. Pagination and rendering

```mermaid
flowchart LR
    Raw["Parsed or cached chapter content"] --> Process["ContentProcessor"]
    Style["Reading config, fonts and viewport"] --> Provider["ChapterProvider"]
    Process --> Provider
    Provider --> Chapter["TextChapter with asynchronous layout"]
    Chapter --> Pages["TextPage objects"]
    Pages --> Factory["TextPageFactory selects current and adjacent pages"]
    Factory --> Draw["PageView and ContentTextView draw the page"]
    Gesture["ReadView and animation delegate"] --> Factory
    Gesture --> Progress["ReadBook position changes"]
    Progress --> Save["Persist chapter index and character position"]
```

The [page entities](../app/src/main/java/io/legado/app/ui/book/read/page/entities)
are a display model, distinct from Room's `BookChapter`. A database chapter does
not have a stable count of screen pages: changing font size, spacing, orientation
or viewport can change pagination.

[TextPageFactory](../app/src/main/java/io/legado/app/ui/book/read/page/provider/TextPageFactory.kt)
selects neighboring pages/chapters.
[ReadView](../app/src/main/java/io/legado/app/ui/book/read/page/ReadView.kt)
owns the page views and chooses the
[animation delegate](../app/src/main/java/io/legado/app/ui/book/read/page/delegate).
[ContentTextView](../app/src/main/java/io/legado/app/ui/book/read/page/ContentTextView.kt)
draws content. The normal reader is not a WebView displaying an entire website.

`ReadBook.contentLoadFinish` processes content and consumes the chapter's layout
channel, updating the screen as pages become ready. This is why a layout change
must preserve reading position and handle work already in progress.

Persisted progress uses a chapter index and in-chapter character position rather
than a permanent screen page number. See [Data and sync](data-and-sync.md).

## 4. Selection and dictionary handoff

```mermaid
sequenceDiagram
    actor User
    participant View as ReadView and WordSelection
    participant Activity as ReadBookActivity
    participant Help as ProcessTextHelp
    participant App as Selected dictionary app
    User->>View: Long-press, optionally drag, then release
    View->>View: Resolve word-aligned selection
    View->>Activity: onTextSelectionComplete
    alt API 23+, automatic opening enabled and target configured
        Activity->>Help: launch(target, selectedText)
        Help->>Help: Revalidate eligible activity
        alt Launch succeeds
            Help->>App: Explicit ACTION_PROCESS_TEXT intent
            Help-->>Activity: Success
            Activity->>View: Clear selection
        else Target unavailable or launch rejected
            Help-->>Activity: Report error and return failure
            Activity->>Activity: Show normal text-action menu
        end
    else Normal selection mode
        Activity->>Activity: Show normal text-action menu
    end
```

[WordSelection](../app/src/main/java/io/legado/app/ui/book/read/page/WordSelection.kt)
supports whole-word long-press dragging. The separate handles remain
character-precise. Gesture handling must distinguish selection from page turns;
[PageTurnGesture](../app/src/main/java/io/legado/app/ui/book/read/page/PageTurnGesture.kt)
is part of that behavior.

The external-app feature is opt-in through
[reading settings](../app/src/main/java/io/legado/app/ui/book/read/config/MoreConfigDialog.kt).
[ProcessTextHelp](../app/src/main/java/io/legado/app/help/ProcessTextHelp.kt)
discovers eligible Android text-processing activities and launches the chosen
component with read-only text. It is not a proprietary dictionary API.
The normal [TextActionMenu](../app/src/main/java/io/legado/app/ui/book/read/TextActionMenu.kt)
also offers built-in actions; built-in dictionary rules are a separate mechanism.

## 5. A practical code-reading and testing route

1. Follow `ReadBookViewModel.initData` to `initBook`.
2. Follow `ReadBook.loadContent` through a cache hit before studying downloads.
3. Follow `contentLoadFinish` into `ChapterProvider.getTextChapterAsync`.
4. Compare the persisted `BookChapter` with an in-memory `TextChapter`.
5. Follow one page turn and then a text-selection completion callback.

Useful existing checks:

- [WordSelectionTest](../app/src/test/java/io/legado/app/ui/book/read/page/WordSelectionTest.kt)
  and [PageTurnGestureTest](../app/src/test/java/io/legado/app/ui/book/read/page/PageTurnGestureTest.kt)
  cover focused JVM behavior.
- [TextSelectionAppDeviceTest](../app/src/androidTest/java/io/legado/app/TextSelectionAppDeviceTest.kt)
  covers Android integration.
- [Development regression checks](../DEVELOPING.md#9-run-device-tests)
  describe selection, gesture and TTS checks on a device.

When debugging, first decide whether the defect is **content acquisition,
content transformation, layout, rendering or input handling**. The same visible
symptom can originate in very different parts of this pipeline.
