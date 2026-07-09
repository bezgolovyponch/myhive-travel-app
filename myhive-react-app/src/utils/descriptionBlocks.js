// Admin-entered descriptions are plain text; a line ending with ":" is treated
// as a sub-heading (mockup: "What to expect on the day", "Good to know").
export function parseDescriptionBlocks(description) {
    return (description || '')
        .split(/\n+/)
        .map(line => line.trim())
        .filter(Boolean)
        .map(line => line.endsWith(':')
            ? {type: 'heading', text: line.slice(0, -1).trim()}
            : {type: 'paragraph', text: line});
}
