export function toggleArrayItem(array, item) {
    return array.includes(item)
        ? array.filter(id => id !== item)
        : [...array, item];
}
