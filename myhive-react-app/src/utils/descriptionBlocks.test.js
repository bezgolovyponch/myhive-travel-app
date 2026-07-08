import {parseDescriptionBlocks} from './descriptionBlocks';

test('lines ending with a colon become sub-headings', () => {
    const text = 'Intro paragraph.\nWhat to expect on the day:\nKarts and helmets.\nGood to know:\nBring a licence.';
    expect(parseDescriptionBlocks(text)).toEqual([
        {type: 'paragraph', text: 'Intro paragraph.'},
        {type: 'heading', text: 'What to expect on the day'},
        {type: 'paragraph', text: 'Karts and helmets.'},
        {type: 'heading', text: 'Good to know'},
        {type: 'paragraph', text: 'Bring a licence.'},
    ]);
});

test('empty and missing descriptions produce no blocks', () => {
    expect(parseDescriptionBlocks('')).toEqual([]);
    expect(parseDescriptionBlocks(null)).toEqual([]);
});
