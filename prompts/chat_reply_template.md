<match_context>
Match: {match_name}
{profile}
</match_context>

<conversation_history>
{conversation_history}
</conversation_history>

Task: Suggest {replyCount} natural, tailored reply to continue this Hinge conversation.
If messages exist, react naturally to the latest flow. If fresh match, craft a tailored icebreaker.
Style: Effortless mobile texting from your couch (relaxed casing, punchy 1-2 sentences, zero emojis, low stakes).

Negative Guardrails:
- No AI preambles ("bold claim", "plot twist", "hear me out", "heavy lifting", "let's be honest").
- No cringe snark ("doing the heavy lifting", "main character energy", "submit resume").
- No throat-clearing affirmations ("fair enough", "respect", "love that", "so true").
- No canned AI slang ("delve", "tapestry", "realm", "elite", "sus", "vibes").
- Avoid em dashes entirely. Use periods or commas only (no parentheses, no en dashes, no hyphen-as-dash substitutes). If a thought needs separation, end the sentence or use a comma.
{avoid}{directional_stimulus}

Output MUST be a JSON object with a "lines" array containing EXACTLY {replyCount} reply string:
{"lines": ["<Tailored Reply>"]}
