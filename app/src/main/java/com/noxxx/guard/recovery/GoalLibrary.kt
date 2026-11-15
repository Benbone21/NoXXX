package com.noxxx.guard.recovery

enum class GoalSection(val label: String) {
    SPIRITUAL("Spiritual"), NIGHT("At night and alone"), CONNECTION("Connection"), HEALTH("Health"), RECOVERY("Recovery"),
}

/**
 * Built-in goals offered during setup. Ids are stored with each day's check-offs, so they must never change;
 * the first nine are the ids earlier versions used.
 */
object GoalLibrary {
    data class Entry(val goal: Goal, val section: GoalSection)

    private fun e(id: String, title: String, section: GoalSection) = Entry(Goal(id, title), section)

    val all = listOf(
        e("pray-morning", "Morning prayer", GoalSection.SPIRITUAL),
        e("pray-evening", "Evening prayer", GoalSection.SPIRITUAL),
        e("scriptures", "Scripture study", GoalSection.SPIRITUAL),
        e("cfm", "Come, Follow Me study", GoalSection.SPIRITUAL),
        e("serve", "Serve someone", GoalSection.SPIRITUAL),
        e("temple", "Go to the temple (weekly)", GoalSection.SPIRITUAL),
        e("sacrament", "Take the sacrament worthily (Sunday)", GoalSection.SPIRITUAL),
        e("hymn", "Sing or listen to a hymn", GoalSection.SPIRITUAL),
        e("phone-away", "Phone out of the bedroom at night", GoalSection.NIGHT),
        e("phone-off", "Phone off by a set time", GoalSection.NIGHT),
        e("shared-spaces", "Use devices only in shared spaces", GoalSection.NIGHT),
        e("reach-out", "Reach out to someone", GoalSection.CONNECTION),
        e("check-in", "Weekly check-in with my support person", GoalSection.CONNECTION),
        e("arp", "Attend an ARP meeting", GoalSection.CONNECTION),
        e("bishop", "Meet with my bishop", GoalSection.CONNECTION),
        e("move", "Exercise or go for a walk", GoalSection.HEALTH),
        e("sleep", "In bed by a set time", GoalSection.HEALTH),
        e("outside", "Time outside", GoalSection.HEALTH),
        e("journal", "Write in my journal", GoalSection.RECOVERY),
        e("note-trigger", "Note what triggered an urge", GoalSection.RECOVERY),
        e("talk", "Read or listen to a recovery talk", GoalSection.RECOVERY),
    )

    val ids = all.map { it.goal.id }.toSet()

    fun goal(id: String) = all.first { it.goal.id == id }.goal

    fun isBuiltIn(id: String) = id in ids
}
