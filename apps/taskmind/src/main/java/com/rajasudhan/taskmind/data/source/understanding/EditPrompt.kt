package com.rajasudhan.taskmind.data.source.understanding

/**
 * The constrained-JSON prompt for a natural-language EDIT of one existing item (#115). Unlike the
 * extraction prompt it returns a PATCH — only the fields that should change — so an omitted field is
 * left untouched (see [SuggestionEdit] for the presence semantics). Dates are still resolved on-device
 * by [com.rajasudhan.taskmind.data.source.NaturalDate], which overrides the model for date/time/recurrence.
 */
object EditPrompt {
    const val INSTRUCTION = """You are editing ONE existing item. The user message gives you the item's
current fields and a plain-language change request:

  Current item:
    title: <text>
    type: <"reminder" | "todo" | "note" | "waiting_on">
    due_date: <YYYY-MM-DD or null>
    due_time: <HH:MM 24-hour, or null>
    location: <a place/venue, or null>
    recurrence: <"daily" | "weekly" | "monthly" or null>
    priority: <"low" | "normal" | "high">
  Instruction: <what to change>

The current date and time is: {{CURRENT_DATETIME}}
Use it to turn relative references ("friday", "tomorrow 6pm", "in two weeks") into absolute values.

Return ONLY a JSON object containing ONLY the fields that should CHANGE — omit every field that stays the
same. Use null to CLEAR a field. No markdown, no code fences, no commentary. If nothing should change,
return {}. Field names and allowed values are exactly as listed above.

Examples:

Instruction: "make it friday 6pm, high priority"
{"due_date": "2026-06-12", "due_time": "18:00", "priority": "high"}

Instruction: "at the Indiranagar branch"
{"location": "Indiranagar branch"}

Instruction: "remind me every week"
{"recurrence": "weekly"}

Instruction: "it's just a note now, and remove the time"
{"type": "note", "due_time": null}

Instruction: "remove the due date"
{"due_date": null}

Instruction: "call it 'renew passport'"
{"title": "Renew passport"}
"""
}
