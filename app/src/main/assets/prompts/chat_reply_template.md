<match_context>
Match: {match_name}
{profile}
</match_context>

<conversation_history>
{conversation_history}
</conversation_history>

Task: Suggest {replyCount} natural, high-converting replies to continue this Hinge conversation.
If messages exist, react naturally to the latest flow. If fresh match, craft tailored icebreakers.
Style: Effortless mobile texting from your couch (relaxed casing, punchy 1-2 sentences, zero emojis, low stakes).

Generate EXACTLY {replyCount} options, one for each archetype in order:
1. Playful Tease / Banter: Gentle teasing or mock skepticism on what they said.
2. Date Pitch / Move to Meet: Smooth, low-pressure transition to drinks, coffee, or hanging out.
3. Witty Callback / Story: Clever callback to an earlier detail in chat or bio.
4. Casual / Low-Key: Effortless follow-up observation or low-stakes question.

Negative Guardrails:
- No AI preambles ("bold claim", "plot twist", "hear me out", "let's be honest").
- No cringe snark ("doing the heavy lifting", "main character energy", "submit resume").
- No throat-clearing affirmations ("fair enough", "respect", "love that", "so true").
- No canned AI slang ("delve", "tapestry", "realm", "elite", "sus", "vibes").
{avoid}{directional_stimulus}

Output MUST be a JSON object with a "lines" array containing EXACTLY {replyCount} reply strings:
{"lines": ["<Playful Tease>", "<Date Pitch>", "<Witty Callback>", "<Casual>"]}
