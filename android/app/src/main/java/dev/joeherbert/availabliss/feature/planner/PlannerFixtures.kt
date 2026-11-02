package dev.joeherbert.availabliss.feature.planner

/**
 * A real-shaped planner answer for Compose previews and the parsing test. Never used
 * at runtime: the dev server has no Anthropic key, so this is how the result UI is checked.
 */
object PlannerFixtures {
    const val RESULT_JSON = """
{
  "best": {
    "slot": { "start": "2026-11-27T18:00:00.000Z", "end": "2026-11-29T23:59:00.000Z", "label": "Fri 27 – Sun 29 Nov" },
    "why": "The only weekend everyone synced is free, and it's before the December rush. Nobody has to take time off."
  },
  "alternatives": [
    { "slot": { "start": "2026-12-11T18:00:00.000Z", "end": "2026-12-13T23:59:00.000Z", "label": "Fri 11 – Sun 13 Dec" }, "why": "Also clear for everyone, but closer to Christmas, so cottages cost more." }
  ],
  "ideas": [
    { "title": "Catbells at sunrise", "description": "A short, steep classic near Keswick with huge views; fine for mixed fitness." },
    { "title": "Pub quiz in Keswick", "description": "Saturday night at the Dog & Gun, low effort and cheap." }
  ],
  "checklist": ["Book the cottage this week", "Sort out who's driving", "Check the forecast on Thursday"],
  "proposalTitle": "Lakes weekend",
  "proposalNote": "Fri evening to Sunday. Catbells on Saturday morning if the weather holds."
}
"""
}
