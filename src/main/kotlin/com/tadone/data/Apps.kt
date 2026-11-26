package com.tadone.data

/** An app the student can connect through Composio, shown on the Apps screen. */
data class AppInfo(val slug: String, val name: String, val blurb: String, val group: String, val color: Long)

object Apps {
    val all = listOf(
        AppInfo("google_classroom", "Google Classroom", "Sync coursework, turn in and unsubmit", "Google", 0xFF1E8E3E),
        AppInfo("googledrive", "Google Drive", "Upload files to attach to submissions", "Google", 0xFF1A73E8),
        AppInfo("googledocs", "Google Docs", "Create and edit essays and reports", "Google", 0xFF4285F4),
        AppInfo("googlesheets", "Google Sheets", "Data tables, grade trackers, lab results", "Google", 0xFF0F9D58),
        AppInfo("googleslides", "Google Slides", "Presentations for group projects", "Google", 0xFFF4B400),
        AppInfo("googleforms", "Google Forms", "Quizzes and surveys", "Google", 0xFF7248B9),
        AppInfo("googlecalendar", "Google Calendar", "Put deadlines and study blocks on your calendar", "Google", 0xFF4285F4),
        AppInfo("googletasks", "Google Tasks", "Mirror your to-dos", "Google", 0xFF1A73E8),
        AppInfo("gmail", "Gmail", "Read class announcements and email teachers", "Google", 0xFFEA4335),
        AppInfo("googlemeet", "Google Meet", "Schedule study sessions", "Google", 0xFF00897B),
        AppInfo("notion", "Notion", "Notes and study wikis", "Productivity", 0xFF2F2F2F),
        AppInfo("todoist", "Todoist", "Import tasks from Todoist", "Productivity", 0xFFE44332),
        AppInfo("trello", "Trello", "Group project boards", "Productivity", 0xFF0079BF),
        AppInfo("canvas", "Canvas LMS", "Courses and assignments from Canvas", "School", 0xFFE13F2B),
        AppInfo("microsoft_teams", "Microsoft Teams", "Class teams and assignments", "School", 0xFF5059C9),
        AppInfo("outlook", "Outlook", "School email and calendar", "School", 0xFF0078D4),
        AppInfo("one_drive", "OneDrive", "Files from your school Microsoft account", "School", 0xFF0364B8),
        AppInfo("zoom", "Zoom", "Join and schedule online classes", "School", 0xFF2D8CFF),
        AppInfo("slack", "Slack", "Class and club workspaces", "Chat", 0xFF4A154B),
        AppInfo("discord", "Discord", "Study group servers", "Chat", 0xFF5865F2),
        AppInfo("github", "GitHub", "Code assignments and repos", "Chat", 0xFF24292F),
        AppInfo("youtube", "YouTube", "Save lecture videos", "Chat", 0xFFFF0000),
    )
    fun byId(slug: String) = all.firstOrNull { it.slug == slug }
}
