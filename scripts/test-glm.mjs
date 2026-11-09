import ZAI from 'z-ai-web-dev-sdk';
const zai = await ZAI.create();
// Test 1: default model
try {
  const r = await zai.chat.completions.create({ messages: [{ role: 'user', content: 'Say OK in 1 word' }], thinking: { type: 'disabled' } });
  console.log('DEFAULT MODEL OK:', r.choices[0]?.message?.content?.slice(0, 50), '| model:', r.model);
} catch (e) { console.log('DEFAULT FAIL:', e.message); }
// Test 2: glm-5.3 explicit
try {
  const r = await zai.chat.completions.create({ model: 'glm-5.3', messages: [{ role: 'user', content: 'Say OK' }], thinking: { type: 'disabled' } });
  console.log('GLM-5.3 OK:', r.choices[0]?.message?.content?.slice(0, 50), '| model:', r.model);
} catch (e) { console.log('GLM-5.3 FAIL:', e.message); }
// Test 3: web_search
try {
  const s = await zai.functions.invoke('web_search', { query: 'privacy browser', num: 3 });
  console.log('SEARCH OK:', JSON.stringify(s).slice(0, 200));
} catch (e) { console.log('SEARCH FAIL:', e.message); }
