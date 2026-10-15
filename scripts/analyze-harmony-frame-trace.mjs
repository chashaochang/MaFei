import { createReadStream } from 'node:fs';
import { createInterface } from 'node:readline';

// Input: hitrace --text app ace graphic; this measures application buffer
// submission cadence, not display presentation FPS or time spent on the GPU.
const [path, pid] = process.argv.slice(2);
if (!path || !/^\d+$/.test(pid ?? '')) {
    throw new Error('Usage: node scripts/analyze-harmony-frame-trace.mjs trace.ftrace appPid');
}
const event = new RegExp(`-(\\d+)\\s+\\(\\s*${pid}\\)\\s+\\[\\d+\\]\\s+\\S+\\s+(\\d+\\.\\d+): tracing_mark_write: ([BE])\\|${pid}(?:\\|(.*))?`);
const stacks = new Map();
const sections = new Map();
const frames = [];
for await (const line of createInterface({ input: createReadStream(path), crlfDelay: Infinity })) {
    const match = line.match(event);
    if (!match) continue;
    const [, tid, seconds, phase, payload = ''] = match;
    const time = Number(seconds);
    const stack = stacks.get(tid) ?? [];
    stacks.set(tid, stack);
    if (phase === 'B') {
        const name = payload.split('|')[0];
        stack.push({ time, name });
        if (name === 'H:NativeWindowFlushBuffer') frames.push(time);
    } else {
        const begin = stack.pop();
        if (!begin || !/Compose|Recomposer|DispatchDisplaySync|Load\/Compile Shaders/.test(begin.name)) continue;
        const durations = sections.get(begin.name) ?? [];
        durations.push((time - begin.time) * 1000);
        sections.set(begin.name, durations);
    }
}
function summarize(values) {
    if (!values.length) return null;
    const sorted = [...values].sort((a, b) => a - b);
    const round = n => Math.round(n * 1000) / 1000;
    return {
        samples: values.length,
        meanMs: round(values.reduce((sum, n) => sum + n, 0) / values.length),
        p50Ms: round(sorted[Math.floor(sorted.length * .5)]),
        p95Ms: round(sorted[Math.floor(sorted.length * .95)]),
        maxMs: round(sorted.at(-1)),
    };
}
frames.sort((a, b) => a - b);
const gaps = frames.slice(1).map((time, i) => (time - frames[i]) * 1000);
console.log(JSON.stringify({
    path, pid,
    measurement: 'application buffer submission intervals, not display FPS',
    durationSeconds: frames.length > 1 ? frames.at(-1) - frames[0] : 0,
    intervals: summarize(gaps),
    intervalsOver25Ms: gaps.filter(ms => ms > 25).length,
    intervalsOver50Ms: gaps.filter(ms => ms > 50).length,
    sections: [...sections].map(([name, durations]) => ({ name, ...summarize(durations) })),
}, null, 2));
