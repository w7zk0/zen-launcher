export type AppId =
  | "phone"
  | "messages"
  | "camera"
  | "maps"
  | "notes"
  | "calendar"
  | "clock"
  | "music"
  | "photos"
  | "mail"
  | "browser"
  | "weather"
  | "files"
  | "settings"
  | "calculator"
  | "podcasts";

export type ZenApp = {
  id: AppId;
  name: string;
};

export const CATALOG: ZenApp[] = [
  { id: "phone", name: "Phone" },
  { id: "messages", name: "Messages" },
  { id: "camera", name: "Camera" },
  { id: "maps", name: "Maps" },
  { id: "notes", name: "Notes" },
  { id: "calendar", name: "Calendar" },
  { id: "clock", name: "Clock" },
  { id: "music", name: "Music" },
  { id: "photos", name: "Photos" },
  { id: "mail", name: "Mail" },
  { id: "browser", name: "Browser" },
  { id: "weather", name: "Weather" },
  { id: "files", name: "Files" },
  { id: "settings", name: "Settings" },
  { id: "calculator", name: "Calculator" },
  { id: "podcasts", name: "Podcasts" },
];

export type Align = "left" | "center" | "right";
export type Size = "s" | "m" | "l";
export type ThemeName = "night" | "day";
export type Wall = "daily" | "mist" | "shore" | "night";

export type ZenData = {
  theme: ThemeName;
  align: Align;
  size: Size;
  wall: Wall;
  showClock: boolean;
  showDate: boolean;
  hour24: boolean;
  showStatus: boolean;
  home: string[];
  hidden: string[];
  renames: Record<string, string>;
  swipeLeft: string;
  swipeRight: string;
  seenDrawer: boolean;
  doubleTapLock: boolean;
};

export const DEFAULT_ZEN: ZenData = {
  theme: "night",
  align: "center",
  size: "m",
  wall: "daily",
  showClock: true,
  showDate: true,
  hour24: false,
  showStatus: true,
  home: ["phone", "messages", "camera", "maps", "notes"],
  hidden: [],
  renames: {},
  swipeLeft: "music",
  swipeRight: "camera",
  seenDrawer: false,
  doubleTapLock: false,
};

const WALLS = ["mist", "shore", "night"] as const;

export function resolveWall(choice: Wall, date: Date) {
  if (choice !== "daily") return choice;
  const start = new Date(date.getFullYear(), 0, 0);
  const day = Math.floor((date.getTime() - start.getTime()) / 86400000);
  return WALLS[((day % 3) + 3) % 3];
}

export function appById(id: string) {
  return CATALOG.find((app) => app.id === id);
}

export function labelOf(id: string, renames: Record<string, string>) {
  return renames[id] || appById(id)?.name || id;
}

export function isAppId(id: string): id is AppId {
  return CATALOG.some((app) => app.id === id);
}
