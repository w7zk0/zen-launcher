import { useEffect, useRef, useState, type PointerEvent as ReactPointerEvent } from "react";
import {
  CATALOG,
  DEFAULT_ZEN,
  type Align,
  type AppId,
  type FontStyle,
  type Size,
  type ThemeName,
  type Wall,
  type ZenData,
  isAppId,
  labelOf,
  resolveWall,
} from "@/lib/zen/catalog";

const KEY = "zen.launcher.v1";

type Screen =
  | "home"
  | "drawer"
  | "settings"
  | "shade"
  | "lock"
  | "app"
  | "menu"
  | "rename"
  | "pick";

type PickPurpose = "left" | "right" | "add";
type Layer = "home" | "drawer";

const SIZES = ["s", "m", "l"] as const;
const ALIGNS = ["left", "center", "right"] as const;
const THEMES = ["night", "day"] as const;
const FONTS = ["zen", "workbench"] as const;
const WALL_CHOICES = ["daily", "mist", "shore", "night"] as const;

function cn(...parts: Array<string | false | null | undefined>) {
  return parts.filter(Boolean).join(" ");
}

function cycle<T extends string>(current: T, options: readonly T[]): T {
  const index = options.indexOf(current);
  return options[(index + 1) % options.length];
}

function sizeLabel(size: Size) {
  if (size === "s") return "Small";
  if (size === "l") return "Large";
  return "Medium";
}

function alignLabel(align: Align) {
  if (align === "left") return "Left";
  if (align === "right") return "Right";
  return "Center";
}

function wallLabel(wall: Wall) {
  if (wall === "mist") return "Mist";
  if (wall === "shore") return "Shore";
  if (wall === "night") return "Night";
  return "Daily";
}

function themeLabel(theme: ThemeName) {
  return theme === "day" ? "Day" : "Night";
}

function fontLabel(font: FontStyle) {
  return font === "workbench" ? "Workbench" : "Zen";
}

function formatClock(date: Date, hour24: boolean) {
  const minute = date.getMinutes().toString().padStart(2, "0");
  if (hour24) {
    return { face: `${date.getHours().toString().padStart(2, "0")}:${minute}`, suffix: "" };
  }
  const raw = date.getHours();
  const hour = raw % 12 === 0 ? 12 : raw % 12;
  return { face: `${hour}:${minute}`, suffix: raw < 12 ? "am" : "pm" };
}

function formatDate(date: Date) {
  return date.toLocaleDateString(undefined, {
    weekday: "long",
    day: "numeric",
    month: "long",
  });
}

function sanitize(input: Partial<ZenData>): ZenData {
  const known = new Set(CATALOG.map((app) => app.id));
  const home = Array.isArray(input.home)
    ? input.home.filter((id) => known.has(id as AppId))
    : DEFAULT_ZEN.home;
  const hidden = Array.isArray(input.hidden)
    ? input.hidden.filter((id) => known.has(id as AppId))
    : DEFAULT_ZEN.hidden;
  const renames: Record<string, string> = {};
  if (input.renames && typeof input.renames === "object") {
    for (const [id, name] of Object.entries(input.renames)) {
      if (known.has(id as AppId) && typeof name === "string" && name.trim()) renames[id] = name.trim();
    }
  }
  const theme: ThemeName = input.theme === "day" ? "day" : "night";
  const font: FontStyle = input.font === "workbench" ? "workbench" : "zen";
  const align: Align =
    input.align === "left" || input.align === "right" || input.align === "center"
      ? input.align
      : "center";
  const size: Size = input.size === "s" || input.size === "l" || input.size === "m" ? input.size : "m";
  const wall: Wall =
    input.wall === "mist" || input.wall === "shore" || input.wall === "night" || input.wall === "daily"
      ? input.wall
      : "daily";
  return {
    ...DEFAULT_ZEN,
    ...input,
    theme,
    font,
    align,
    size,
    wall,
    home,
    hidden,
    renames,
    showClock: input.showClock !== false,
    showDate: input.showDate !== false,
    hour24: Boolean(input.hour24),
    showStatus: input.showStatus !== false,
    seenDrawer: Boolean(input.seenDrawer),
    doubleTapLock: Boolean(input.doubleTapLock),
    swipeLeft: typeof input.swipeLeft === "string" ? input.swipeLeft : DEFAULT_ZEN.swipeLeft,
    swipeRight: typeof input.swipeRight === "string" ? input.swipeRight : DEFAULT_ZEN.swipeRight,
  };
}

export function ZenScreen() {
  const [data, setData] = useState<ZenData>(DEFAULT_ZEN);
  const [ready, setReady] = useState(false);
  const [mounted, setMounted] = useState(false);
  const [now, setNow] = useState<Date | null>(null);
  const [screen, setScreen] = useState<Screen>("home");
  const [menuFrom, setMenuFrom] = useState<Layer>("home");
  const [activeId, setActiveId] = useState<string | null>(null);
  const [pick, setPick] = useState<PickPurpose>("add");
  const [appId, setAppId] = useState<AppId>("notes");
  const [query, setQuery] = useState("");
  const [rename, setRename] = useState("");
  const searchRef = useRef<HTMLInputElement>(null);
  const lastTap = useRef(0);
  const pressTimer = useRef<number | null>(null);
  const moved = useRef(false);
  const longFired = useRef(false);
  const start = useRef({ x: 0, y: 0 });

  useEffect(() => {
    setMounted(true);
    try {
      const raw = localStorage.getItem(KEY);
      if (raw) setData(sanitize(JSON.parse(raw) as Partial<ZenData>));
    } catch {
      /* keep defaults */
    }
    setReady(true);
  }, []);

  useEffect(() => {
    if (!ready) return;
    localStorage.setItem(KEY, JSON.stringify(data));
  }, [data, ready]);

  useEffect(() => {
    const tick = () => setNow(new Date());
    tick();
    const id = window.setInterval(tick, 1000);
    return () => window.clearInterval(id);
  }, []);

  useEffect(() => {
    if (screen === "drawer") searchRef.current?.focus();
  }, [screen]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        back();
        return;
      }
      if (event.target instanceof HTMLInputElement || event.target instanceof HTMLTextAreaElement) return;
      if (screen !== "home") return;
      if (event.key === "ArrowUp") {
        event.preventDefault();
        openDrawer();
      } else if (event.key === "ArrowDown") {
        event.preventDefault();
        setScreen("shade");
      } else if (event.key === "ArrowLeft") launchId(data.swipeLeft);
      else if (event.key === "ArrowRight") launchId(data.swipeRight);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  });

  const wall = mounted ? resolveWall(data.wall, now ?? new Date()) : "mist";
  const clock = now ? formatClock(now, data.hour24) : null;
  const alignClass =
    data.align === "left" ? "items-start text-left" : data.align === "right" ? "items-end text-right" : "items-center text-center";
  const clockClass = data.size === "s" ? "text-6xl" : data.size === "l" ? "text-8xl" : "text-7xl";
  const appClass = data.size === "s" ? "text-lg" : data.size === "l" ? "text-4xl" : "text-2xl";
  const label = (id: string) => labelOf(id, data.renames);
  const visible = CATALOG.filter((app) => !data.hidden.includes(app.id)).sort((a, b) =>
    label(a.id).localeCompare(label(b.id)),
  );
  const homeApps = data.home.filter((id) => isAppId(id) && !data.hidden.includes(id));
  const q = query.trim().toLowerCase();
  const matches = visible.filter((app) => !q || label(app.id).toLowerCase().includes(q));

  function patch(partial: Partial<ZenData>) {
    setData((current) => ({ ...current, ...partial }));
  }

  function openDrawer() {
    setQuery("");
    setScreen("drawer");
    patch({ seenDrawer: true });
  }

  function launchId(id: string) {
    if (!isAppId(id) || data.hidden.includes(id)) return;
    setAppId(id);
    setQuery("");
    setScreen("app");
  }

  function onSearch(next: string) {
    setQuery(next);
    if (next.startsWith(" ")) return;
    const needle = next.trim().toLowerCase();
    if (!needle) return;
    const found = visible.filter((app) => label(app.id).toLowerCase().includes(needle));
    if (found.length === 1) launchId(found[0].id);
  }

  function back() {
    if (screen === "menu" || screen === "rename") setScreen(menuFrom);
    else if (screen === "pick") setScreen("settings");
    else setScreen("home");
  }

  function openMenu(id: string, from: Layer) {
    setActiveId(id);
    setMenuFrom(from);
    setScreen("menu");
  }

  function clearPress() {
    if (pressTimer.current) window.clearTimeout(pressTimer.current);
    pressTimer.current = null;
  }

function onHomeDown(event: ReactPointerEvent<HTMLDivElement>) {
    moved.current = false;
    longFired.current = false;
    start.current = { x: event.clientX, y: event.clientY };
    const target = (event.target as HTMLElement).closest("[data-app]");
    const id = target?.getAttribute("data-app");
    clearPress();
    pressTimer.current = window.setTimeout(() => {
      longFired.current = true;
      if (id) openMenu(id, "home");
      else setScreen("settings");
    }, 480);
  }

  function onHomeMove(event: ReactPointerEvent<HTMLDivElement>) {
    const dx = event.clientX - start.current.x;
    const dy = event.clientY - start.current.y;
    if (dx * dx + dy * dy > 144) {
      moved.current = true;
      clearPress();
    }
  }

  function onHomeUp(event: ReactPointerEvent<HTMLDivElement>) {
    clearPress();
    const dx = event.clientX - start.current.x;
    const dy = event.clientY - start.current.y;
    if (moved.current && (Math.abs(dx) > 56 || Math.abs(dy) > 56)) {
      if (Math.abs(dy) > Math.abs(dx)) {
        if (dy < 0) openDrawer();
        else setScreen("shade");
      } else if (dx < 0) launchId(data.swipeLeft);
      else launchId(data.swipeRight);
      return;
    }
    if (longFired.current) return;
    const hit = (event.target as HTMLElement).closest("[data-app]");
    if (hit) return;
    const stamp = Date.now();
    if (stamp - lastTap.current < 320) {
      lastTap.current = 0;
      if (data.doubleTapLock) setScreen("lock");
    } else lastTap.current = stamp;
  }

  const showHint = !data.seenDrawer || homeApps.length === 0;

  return (
    <main className="min-h-dvh bg-paper text-ink lg:flex lg:items-center lg:justify-center lg:gap-16 lg:px-10">
      <section
        className={cn(
          "zen-stage relative mx-auto overflow-hidden bg-ink",
          data.theme === "day" ? "theme-day" : "theme-night",
          data.font === "workbench" && "font-workbench",
        )}
        aria-label="Zen home screen"
      >
        <img src={`/wallpapers/${wall}.jpg`} alt="" className="absolute inset-0 h-full w-full object-cover" />
        <div
          className={cn(
            "absolute inset-0",
            data.theme === "day" ? "bg-paper/80" : "bg-gradient-to-b from-ink/20 via-ink/30 to-ink/70",
          )}
        />

        <div
          className={cn(
            "absolute inset-0 z-10 flex flex-col px-8 pb-8",
            data.showStatus ? "pt-7" : "pt-10",
            screen !== "home" && "pointer-events-none",
          )}
          onPointerDown={screen === "home" ? onHomeDown : undefined}
          onPointerMove={screen === "home" ? onHomeMove : undefined}
          onPointerUp={screen === "home" ? onHomeUp : undefined}
          onPointerCancel={clearPress}
          onContextMenu={(event) => event.preventDefault()}
        >
          {data.showStatus ? (
            <div className="mb-6 flex items-center justify-between text-xs tracking-widest text-fg-dim">
              <span suppressHydrationWarning>{clock ? clock.face : "\u00a0"}</span>
              <span>72</span>
            </div>
          ) : (
            <div className="mb-6" />
          )}

          <div className={cn("flex flex-col", alignClass)}>
            {data.showClock ? (
              <p className={cn("zen-clock font-display font-light leading-none text-fg", clockClass)}>
                {clock ? (
                  <>
                    {clock.face}
                    {clock.suffix ? <span className="zen-suffix">{clock.suffix}</span> : null}
                  </>
                ) : (
                  "\u00a0"
                )}
              </p>
            ) : null}
            {data.showDate ? (
              <p className="mt-3 font-sans text-sm tracking-widest text-fg-dim">{now ? formatDate(now) : "\u00a0"}</p>
            ) : null}
            {showHint ? <p className="mt-6 text-xs tracking-widest text-fg-dim">Swipe up</p> : null}
          </div>

          <div className="flex-1" />
          <div className={cn("flex flex-col", alignClass)}>
            {homeApps.map((id) => (
              <button
                key={id}
                type="button"
                data-app={id}
                onClick={() => {
                  if (longFired.current) {
                    longFired.current = false;
                    return;
                  }
                  if (moved.current) return;
                  launchId(id);
                }}
                className={cn(
                  "w-full py-2.5 font-sans font-light text-fg focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-moss",
                  appClass,
                )}
              >
                {label(id)}
              </button>
            ))}
          </div>
          <div className="h-16" />
        </div>

        {screen === "drawer" ? (
          <div className="absolute inset-0 z-20 flex flex-col bg-sheet px-8 pb-6 pt-7 text-fg">
          <div className="flex items-start justify-between gap-4">
            <input
              ref={searchRef}
              value={query}
              onChange={(event) => onSearch(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  event.preventDefault();
                  const first = matches[0];
                  if (first) launchId(first.id);
                }
              }}
              placeholder="Search"
              aria-label="Search apps"
              autoCapitalize="none"
              autoCorrect="off"
              spellCheck={false}
              className="w-full bg-transparent font-display text-4xl font-light text-fg outline-none placeholder:text-fg-dim"
            />
            <button type="button" onClick={() => setScreen("home")} className="mt-2 shrink-0 py-2 text-sm text-fg-dim">
              Close
            </button>
          </div>
          <div className="mt-3 h-px bg-fg/20" />
          <div className="zen-scroll mt-2 flex-1 overflow-y-auto overscroll-contain">
            {matches.length === 0 ? <p className="py-6 text-fg-dim">Nothing under that name</p> : null}
            <div className={cn("flex flex-col", alignClass)}>
              {matches.map((app) => (
                <button
                  key={app.id}
                  type="button"
                  onClick={() => {
                  if (longFired.current) {
                    longFired.current = false;
                    return;
                  }
                  launchId(app.id);
                }}
                  onContextMenu={(event) => {
                    event.preventDefault();
                    openMenu(app.id, "drawer");
                  }}
                  onPointerDown={() => {
                    clearPress();
                    longFired.current = false;
                    pressTimer.current = window.setTimeout(() => {
                      longFired.current = true;
                      openMenu(app.id, "drawer");
                    }, 480);
                  }}
                  onPointerUp={clearPress}
                  onPointerLeave={clearPress}
                  className={cn("w-full py-3 text-left font-sans font-light text-fg", appClass, alignClass)}
                >
                  {label(app.id)}
                </button>
              ))}
            </div>
          </div>
          {query.trim() === "" ? (
            <p className="pt-3 text-sm text-fg-dim">Type a name. Start with a space to look without opening.</p>
          ) : null}
        </div>
        ) : null}

        {screen === "settings" || screen === "pick" ? (
          <SettingsSheet
            data={data}
            mode={screen === "pick" ? pick : null}
            label={label}
            onClose={() => setScreen("home")}
            onPatch={patch}
            onPick={(purpose) => {
              setPick(purpose);
              setScreen("pick");
            }}
            onChoose={(id) => {
              if (pick === "left") patch({ swipeLeft: id });
              else if (pick === "right") patch({ swipeRight: id });
              else if (id && !data.home.includes(id)) patch({ home: [...data.home, id] });
              setScreen("settings");
            }}
            onMenu={(id) => openMenu(id, "home")}
          />
        ) : null}

        {screen === "shade" ? (
          <button
            type="button"
            className="absolute inset-0 z-20 bg-ink/80 px-8 pt-20 text-left backdrop-blur-md"
            onClick={() => setScreen("home")}
          >
            <p className="text-xs tracking-widest text-fg-dim">NOTIFICATIONS</p>
            <p className="mt-8 font-display text-4xl font-light text-fg">Nothing is asking for you.</p>
            <p className="mt-10 text-sm tracking-widest text-fg-dim">Tap to close</p>
          </button>
        ) : null}

        {screen === "app" ? (
          <AppPane
            id={appId}
            title={label(appId)}
            now={now}
            onHome={() => setScreen("home")}
            onSettings={() => setScreen("settings")}
            onWall={(wallId) => patch({ wall: wallId })}
          />
        ) : null}

        {screen === "lock" ? (
          <button
            type="button"
            className="absolute inset-0 z-40 flex items-center justify-center bg-ink"
            onClick={() => setScreen("home")}
          >
            <span className="font-display text-xl tracking-widest text-stone">Zen</span>
          </button>
        ) : null}

        {screen === "menu" && activeId ? (
          <MenuSheet
            title={label(activeId)}
            onHome={data.home.includes(activeId)}
            hidden={data.hidden.includes(activeId)}
            onClose={() => setScreen(menuFrom)}
            onAction={(action) => {
              if (action === "remove") patch({ home: data.home.filter((id) => id !== activeId) });
              if (action === "add" && !data.home.includes(activeId)) patch({ home: [...data.home, activeId] });
              if (action === "up" || action === "down") {
                const ids = [...data.home];
                const index = ids.indexOf(activeId);
                const next = index + (action === "up" ? -1 : 1);
                if (index >= 0 && next >= 0 && next < ids.length) {
                  const swap = ids[index];
                  const other = ids[next];
                  if (swap && other) {
                    ids[index] = other;
                    ids[next] = swap;
                    patch({ home: ids });
                  }
                }
              }
              if (action === "hide") {
                patch({
                  hidden: data.hidden.includes(activeId)
                    ? data.hidden.filter((id) => id !== activeId)
                    : [...data.hidden, activeId],
                });
              }
              if (action === "rename") {
                setRename(label(activeId));
                setScreen("rename");
                return;
              }
              setScreen(menuFrom === "drawer" && action !== "hide" ? "drawer" : "home");
              if (action === "hide") setScreen("home");
            }}
          />
        ) : null}

        {screen === "rename" && activeId ? (
          <div className="absolute inset-0 z-30 flex items-end bg-ink/50">
            <form
              className="w-full bg-sheet px-8 pb-10 pt-8 text-fg"
              onSubmit={(event) => {
                event.preventDefault();
                const next = { ...data.renames };
                if (rename.trim()) next[activeId] = rename.trim();
                else delete next[activeId];
                patch({ renames: next });
                setScreen(menuFrom);
              }}
            >
              <p className="font-display text-3xl font-light">Rename</p>
              <input
                value={rename}
                onChange={(event) => setRename(event.target.value)}
                aria-label="App name"
                className="mt-6 w-full border-b border-fg/30 bg-transparent pb-2 font-sans text-2xl text-fg outline-none"
                autoFocus
              />
              <div className="mt-8 flex gap-6">
                <button type="submit" className="py-2 text-lg">
                  Save
                </button>
                <button
                  type="button"
                  className="py-2 text-lg text-fg-dim"
                  onClick={() => {
                    const next = { ...data.renames };
                    delete next[activeId];
                    patch({ renames: next });
                    setScreen(menuFrom);
                  }}
                >
                  Reset
                </button>
                <button type="button" className="py-2 text-lg text-fg-dim" onClick={() => setScreen(menuFrom)}>
                  Cancel
                </button>
              </div>
            </form>
          </div>
        ) : null}
      </section>

      <aside className="hidden max-w-md lg:block">
        <p className="text-xs tracking-widest text-moss uppercase">Android launcher</p>
        <h1 className="mt-3 font-display text-7xl font-light leading-none text-ink">Zen</h1>
        <p className="mt-6 max-w-sm text-lg leading-relaxed text-ink-soft">
          A home screen with nothing to pull you back. Words instead of icons, on a quiet photograph.
        </p>
        <a
          href="/zen.apk"
          download="Zen.apk"
          className="mt-8 inline-flex bg-ink px-6 py-3 text-sm tracking-wide text-paper"
        >
          Download for Android
        </a>
        <ul className="mt-8 space-y-2 text-sm leading-relaxed text-stone">
          <li>Swipe up for every app. A few letters opens the only match.</li>
          <li>Start a search with a space if you want to look without opening.</li>
          <li>Long-press the screen for settings. Long-press a name to rename, hide, or move it.</li>
          <li>Install the file, open Zen, then press Home and choose it.</li>
        </ul>
      </aside>
    </main>
  );
}

function SettingsSheet({
  data,
  mode,
  label,
  onClose,
  onPatch,
  onPick,
  onChoose,
  onMenu,
}: {
  data: ZenData;
  mode: PickPurpose | null;
  label: (id: string) => string;
  onClose: () => void;
  onPatch: (partial: Partial<ZenData>) => void;
  onPick: (purpose: PickPurpose) => void;
  onChoose: (id: string) => void;
  onMenu: (id: string) => void;
}) {
  const apps = CATALOG.filter((app) => !data.hidden.includes(app.id));
  return (
    <div className="zen-scroll absolute inset-0 z-20 overflow-y-auto bg-sheet px-8 pb-10 pt-7 text-fg">
      <button type="button" onClick={onClose} className="py-2 text-sm text-fg-dim">
        Close
      </button>
      {mode ? (
        <div className="mt-6">
          <p className="text-xs tracking-widest text-fg-dim">
            {mode === "left" ? "SWIPE LEFT" : mode === "right" ? "SWIPE RIGHT" : "ADD TO HOME"}
          </p>
          {mode !== "add" ? (
            <button type="button" onClick={() => onChoose("")} className="mt-4 block w-full py-3 text-left text-2xl font-light">
              None
            </button>
          ) : null}
          {apps.map((app) => (
            <button
              key={app.id}
              type="button"
              onClick={() => onChoose(app.id)}
              className="block w-full py-3 text-left text-2xl font-light"
            >
              {label(app.id)}
            </button>
          ))}
        </div>
      ) : (
        <div className="mt-4">
          <Section title="Appearance" />
          <Row title="Theme" value={themeLabel(data.theme)} onClick={() => onPatch({ theme: cycle(data.theme, THEMES) })} />
          <Row title="Font" value={fontLabel(data.font)} onClick={() => onPatch({ font: cycle(data.font, FONTS) })} />
          <Row title="Background" value={wallLabel(data.wall)} onClick={() => onPatch({ wall: cycle(data.wall, WALL_CHOICES) })} />
          <Row title="Text" value={sizeLabel(data.size)} onClick={() => onPatch({ size: cycle(data.size, SIZES) })} />
          <Row title="Align" value={alignLabel(data.align)} onClick={() => onPatch({ align: cycle(data.align, ALIGNS) })} />
          <Row title="Clock" value={data.showClock ? "Shown" : "Hidden"} onClick={() => onPatch({ showClock: !data.showClock })} />
          <Row title="Date" value={data.showDate ? "Shown" : "Hidden"} onClick={() => onPatch({ showDate: !data.showDate })} />
          <Row title="Time" value={data.hour24 ? "24-hour" : "12-hour"} onClick={() => onPatch({ hour24: !data.hour24 })} />
          <Row
            title="Status bar"
            value={data.showStatus ? "Shown" : "Hidden"}
            onClick={() => onPatch({ showStatus: !data.showStatus })}
          />

          <Section title="Gestures" />
          <Row title="Swipe left" value={data.swipeLeft ? label(data.swipeLeft) : "None"} onClick={() => onPick("left")} />
          <Row title="Swipe right" value={data.swipeRight ? label(data.swipeRight) : "None"} onClick={() => onPick("right")} />
          <Row
            title="Double-tap"
            value={data.doubleTapLock ? "Lock" : "Off"}
            onClick={() => onPatch({ doubleTapLock: !data.doubleTapLock })}
          />

          <Section title="On your home" />
          {data.home.filter((id) => !data.hidden.includes(id)).length === 0 ? (
            <p className="py-3 text-fg-dim">Swipe up, then long-press a name to keep it here.</p>
          ) : null}
          {data.home
            .filter((id) => !data.hidden.includes(id))
            .map((id) => (
              <Row key={id} title={label(id)} value="Edit" onClick={() => onMenu(id)} />
            ))}
          <Row title="Add an app" value="" onClick={() => onPick("add")} />

          {data.hidden.length > 0 ? (
            <>
              <Section title="Hidden" />
              {data.hidden.map((id) => (
                <Row
                  key={id}
                  title={label(id)}
                  value="Restore"
                  onClick={() => onPatch({ hidden: data.hidden.filter((item) => item !== id) })}
                />
              ))}
            </>
          ) : null}

          <Section title="Android" />
          <a href="/zen.apk" download="Zen.apk" className="block py-3 text-lg">
            Download Zen.apk
          </a>
          <p className="pb-2 text-sm leading-relaxed text-fg-dim">
            Open the file and allow your browser to install it. Then press the phone’s Home button and choose Zen.
          </p>
          <p className="pt-6 text-sm leading-relaxed text-fg-dim">
            Zen is a quiet launcher. No icons, no badges, no account. On a real phone it opens your installed apps,
            and it never uses the network.
          </p>
        </div>
      )}
    </div>
  );
}

function Section({ title }: { title: string }) {
  return <p className="mt-8 mb-1 text-xs tracking-widest text-fg-dim">{title.toUpperCase()}</p>;
}

function Row({ title, value, onClick }: { title: string; value: string; onClick: () => void }) {
  return (
    <button type="button" onClick={onClick} className="flex w-full items-center gap-4 py-3 text-left">
      <span className="flex-1 text-lg font-light">{title}</span>
      {value ? <span className="text-sm text-fg-dim">{value}</span> : null}
    </button>
  );
}

function MenuSheet({
  title,
  onHome,
  hidden,
  onClose,
  onAction,
}: {
  title: string;
  onHome: boolean;
  hidden: boolean;
  onClose: () => void;
  onAction: (action: "remove" | "add" | "up" | "down" | "rename" | "hide") => void;
}) {
  const actions: Array<{ id: "remove" | "add" | "up" | "down" | "rename" | "hide"; label: string }> = [];
  if (onHome) {
    actions.push({ id: "remove", label: "Remove from home" });
    actions.push({ id: "up", label: "Move up" });
    actions.push({ id: "down", label: "Move down" });
  } else actions.push({ id: "add", label: "Add to home" });
  actions.push({ id: "rename", label: "Rename" });
  actions.push({ id: "hide", label: hidden ? "Unhide" : "Hide" });
  return (
    <div className="absolute inset-0 z-30 flex items-end bg-ink/50" onClick={onClose}>
      <div className="w-full bg-sheet px-8 pb-10 pt-8 text-fg" onClick={(event) => event.stopPropagation()}>
        <p className="font-display text-4xl font-light">{title}</p>
        <div className="mt-4">
          {actions.map((action) => (
            <button
              key={action.id}
              type="button"
              onClick={() => onAction(action.id)}
              className="block w-full py-3 text-left text-lg font-light"
            >
              {action.label}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

function AppPane({
  id,
  title,
  now,
  onHome,
  onSettings,
  onWall,
}: {
  id: AppId;
  title: string;
  now: Date | null;
  onHome: () => void;
  onSettings: () => void;
  onWall: (wall: Wall) => void;
}) {
  return (
    <div className={cn("absolute inset-0 z-20 flex flex-col", id === "camera" ? "bg-ink text-paper" : "bg-sheet text-fg")}>
      {id === "camera" ? (
        <CameraPane title={title} onHome={onHome} />
      ) : id === "calculator" ? (
        <CalculatorPane onHome={onHome} />
      ) : (
        <div className="flex h-full flex-col px-8 pt-16 pb-8">
          <p className="font-display text-5xl font-light">{title}</p>
          <div className="mt-8 flex-1">
            <AppBody id={id} now={now} onSettings={onSettings} onWall={onWall} />
          </div>
          <button type="button" onClick={onHome} className="self-center py-3 text-xs tracking-widest text-fg-dim uppercase">
            Home
          </button>
        </div>
      )}
    </div>
  );
}

function AppBody({
  id,
  now,
  onSettings,
  onWall,
}: {
  id: AppId;
  now: Date | null;
  onSettings: () => void;
  onWall: (wall: Wall) => void;
}) {
  const [note, setNote] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [place, setPlace] = useState<string | null>(null);
  const [plate, setPlate] = useState<string | null>(null);
  const notes = [
    { title: "Leave it in the other room", body: "The phone can wait until you know why you picked it up." },
    { title: "Thursday", body: "Nothing is scheduled. Keep it that way if you can." },
    { title: "Names, not icons", body: "If you have to read the word, you meant to open it." },
  ];
  if (id === "phone") return <p className="text-2xl font-light text-fg-dim">No missed calls.</p>;
  if (id === "messages") return <p className="text-2xl font-light text-fg-dim">Nothing unread.</p>;
  if (id === "maps") {
    const places = ["Home", "Work", "Shore"];
    return (
      <div>
        {places.map((item) => (
          <button key={item} type="button" onClick={() => setPlace(item)} className="block py-3 text-left text-2xl font-light">
            {item}
          </button>
        ))}
        {place ? <p className="mt-6 text-fg-dim">A quiet walk from here.</p> : null}
      </div>
    );
  }
  if (id === "notes") {
    const current = notes[note] ?? notes[0];
    return (
      <div>
        {notes.map((item, index) => (
          <button key={item.title} type="button" onClick={() => setNote(index)} className="block py-2 text-left text-xl font-light">
            {item.title}
          </button>
        ))}
        {current ? <p className="mt-6 leading-relaxed text-fg-dim">{current.body}</p> : null}
      </div>
    );
  }
  if (id === "calendar") {
    return (
      <div>
        <p className="font-display text-7xl font-light">{now ? now.getDate() : ""}</p>
        <p className="mt-2 tracking-widest text-fg-dim">{now ? formatDate(now) : ""}</p>
        <p className="mt-8 text-2xl font-light">Nothing scheduled.</p>
      </div>
    );
  }
  if (id === "clock") {
    const clock = now ? formatClock(now, false) : null;
    return (
      <div>
        <p className="zen-clock font-display text-7xl font-light">{clock ? `${clock.face} ${clock.suffix}` : ""}</p>
        <p className="mt-6 text-fg-dim">Alarm is off.</p>
      </div>
    );
  }
  if (id === "music") {
    return (
      <div>
        <p className="text-sm tracking-widest text-fg-dim">NOW</p>
        <p className="mt-3 font-display text-4xl font-light">In a silent way</p>
        <p className="mt-2 text-fg-dim">A room recording</p>
        <button type="button" onClick={() => setPlaying((value) => !value)} className="mt-8 text-2xl font-light">
          {playing ? "Pause" : "Play"}
        </button>
      </div>
    );
  }
  if (id === "photos") {
    const plates: Array<{ id: Wall; name: string }> = [
      { id: "mist", name: "Mist" },
      { id: "shore", name: "Shore" },
      { id: "night", name: "Night" },
    ];
    return (
      <div>
        {plates.map((item) => (
          <button
            key={item.id}
            type="button"
            onClick={() => {
              onWall(item.id);
              setPlate(item.name);
            }}
            className="block py-3 text-left text-2xl font-light"
          >
            {item.name}
          </button>
        ))}
        {plate ? <p className="mt-6 text-fg-dim">{plate} is on the home screen.</p> : null}
      </div>
    );
  }
  if (id === "mail") {
    return (
      <div>
        <p className="text-2xl font-light">No new mail.</p>
        <p className="mt-8 text-sm tracking-widest text-fg-dim">EARLIER</p>
        <p className="mt-3 text-lg">Library — due Thursday</p>
      </div>
    );
  }
  if (id === "browser") {
    return (
      <div>
        <p className="text-sm tracking-widest text-fg-dim">zen.local</p>
        <p className="mt-6 text-lg leading-relaxed">
          The phone is a door, not a room. Zen keeps the door closed until you name what you came for.
        </p>
      </div>
    );
  }
  if (id === "weather") {
    return (
      <div>
        <p className="font-display text-7xl font-light">14°</p>
        <p className="mt-3 text-2xl font-light">Overcast</p>
        <p className="mt-2 text-fg-dim">The air is still.</p>
      </div>
    );
  }
  if (id === "files") {
    return (
      <div>
        {["Documents", "Downloads", "Pictures"].map((item) => (
          <p key={item} className="py-3 text-2xl font-light">
            {item}
          </p>
        ))}
      </div>
    );
  }
  if (id === "settings") {
    return (
      <div>
        {["Display", "Sound"].map((item) => (
          <p key={item} className="py-3 text-2xl font-light text-fg-dim">
            {item}
          </p>
        ))}
        <button type="button" onClick={onSettings} className="py-3 text-left text-2xl font-light">
          Zen launcher
        </button>
      </div>
    );
  }
  if (id === "podcasts") {
    return (
      <div>
        <p className="text-sm tracking-widest text-fg-dim">ON NOT CHECKING</p>
        <p className="mt-3 font-display text-4xl font-light">The empty home screen</p>
        <button type="button" onClick={() => setPlaying((value) => !value)} className="mt-8 text-2xl font-light">
          {playing ? "Playing" : "Play"}
        </button>
      </div>
    );
  }
  return null;
}

function CameraPane({ title, onHome }: { title: string; onHome: () => void }) {
  const [held, setHeld] = useState(false);
  return (
    <div className="flex h-full flex-col items-center justify-end px-8 pb-10">
      <p className="mb-auto pt-16 font-display text-3xl font-light text-paper/80">{held ? "Held" : title}</p>
      <button
        type="button"
        aria-label="Shutter"
        onClick={() => setHeld(true)}
        className="mb-10 h-20 w-20 rounded-full border-2 border-paper/80"
      />
      <button type="button" onClick={onHome} className="py-3 text-xs tracking-widest text-paper/70 uppercase">
        Home
      </button>
    </div>
  );
}

function CalculatorPane({ onHome }: { onHome: () => void }) {
  const [display, setDisplay] = useState("0");
  const [stored, setStored] = useState<number | null>(null);
  const [op, setOp] = useState<string | null>(null);
  const [fresh, setFresh] = useState(false);

  function apply(nextOp: string) {
    const value = Number(display);
    if (stored === null || op === null || fresh) {
      setStored(value);
    } else {
      const result = run(stored, op, value);
      setStored(result);
      setDisplay(formatResult(result));
    }
    setOp(nextOp);
    setFresh(true);
  }

  function input(token: string) {
    if (token === "C") {
      setDisplay("0");
      setStored(null);
      setOp(null);
      setFresh(false);
      return;
    }
    if (token === "=") {
      if (stored === null || op === null) return;
      const result = run(stored, op, Number(display));
      setDisplay(formatResult(result));
      setStored(null);
      setOp(null);
      setFresh(true);
      return;
    }
    if ("+−×÷".includes(token)) {
      apply(token);
      return;
    }
    if (fresh || display === "0") {
      setDisplay(token === "." ? "0." : token);
      setFresh(false);
      return;
    }
    if (token === "." && display.includes(".")) return;
    setDisplay(display + token);
  }

  const keys = ["7", "8", "9", "÷", "4", "5", "6", "×", "1", "2", "3", "−", "0", ".", "=", "+"];
  return (
    <div className="flex h-full flex-col px-6 pt-12 pb-6">
      <p className="zen-clock px-2 text-right font-display text-6xl font-light">{display}</p>
      <div className="mt-auto grid grid-cols-4 gap-2">
        <button type="button" onClick={() => input("C")} className="col-span-4 py-3 text-left text-lg text-fg-dim">
          Clear
        </button>
        {keys.map((key) => (
          <button key={key} type="button" onClick={() => input(key)} className="py-4 text-2xl font-light">
            {key}
          </button>
        ))}
      </div>
      <button type="button" onClick={onHome} className="mt-4 self-center py-3 text-xs tracking-widest text-fg-dim uppercase">
        Home
      </button>
    </div>
  );
}

function run(left: number, op: string, right: number) {
  if (op === "+") return left + right;
  if (op === "−") return left - right;
  if (op === "×") return left * right;
  if (right === 0) return NaN;
  return left / right;
}

function formatResult(value: number) {
  if (!Number.isFinite(value)) return "—";
  const text = String(Math.round(value * 1e6) / 1e6);
  return text;
}
