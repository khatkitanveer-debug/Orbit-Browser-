import ZAI from 'z-ai-web-dev-sdk';
const zai = await ZAI.create();

// Test streaming chat
try {
  const completion = await zai.chat.completions.create({
    model: 'glm-5.3',
    messages: [{ role: 'user', content: '2+2 kitna hai? ek word me batao' }],
    stream: true,
    thinking: { type: 'disabled' },
  });
  console.log('TYPE:', typeof completion);
  console.log('HAS BODY:', !!completion?.body);
  console.log('IS READABLE STREAM:', completion instanceof ReadableStream);
  console.log('CONSTRUCTOR:', completion?.constructor?.name);
  const stream = completion instanceof ReadableStream ? completion : completion?.body ?? null;
  if (stream) {
    const reader = stream.getReader();
    const dec = new TextDecoder();
    let raw = '';
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      raw += dec.decode(value, { stream: true });
      if (raw.length > 1500) break;
    }
    console.log('RAW STREAM (first 1200):', raw.slice(0, 1200));
  } else {
    console.log('CHOICES:', JSON.stringify(completion?.choices?.[0]?.message?.content ?? null)?.slice(0, 200));
  }
} catch (e) {
  console.log('STREAM FAIL:', e.message);
}
