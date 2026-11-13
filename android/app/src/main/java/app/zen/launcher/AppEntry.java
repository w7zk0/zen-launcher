package app.zen.launcher;

public final class AppEntry {
    public final String packageName;
    public final String activityName;
    public final String label;

    public AppEntry(String packageName, String activityName, String label) {
        this.packageName = packageName;
        this.activityName = activityName;
        this.label = label;
    }

    public String id() {
        return packageName + "/" + activityName;
    }
}
