import {renderHook, waitFor} from '@testing-library/react';
import {managerKey, useOrganizerVote} from './useOrganizerVote';
import voteApi from '../services/voteApi';

jest.mock('../services/voteApi');

const SHARE_TOKEN = 'share-1';

beforeEach(() => {
  localStorage.clear();
  localStorage.setItem(managerKey(SHARE_TOKEN), 'manager-1');
  voteApi.getSession.mockResolvedValue({
    shareToken: SHARE_TOKEN, voteMode: 'CART', status: 'ACTIVE', numberOfTravelers: 0,
  });
  voteApi.getTally.mockResolvedValue({status: 'ACTIVE', rows: []});
});

function renderSeeding(dispatch) {
  return renderHook(() => useOrganizerVote({
    shareToken: SHARE_TOKEN,
    managerParam: null,
    restored: true,
    cartEmpty: true,
    dispatch,
    stripManagerParam: jest.fn(),
  }));
}

test('an empty cart is seeded from the ballot with each activity\'s extras', async () => {
  const expectedExtras = [{code: 'private-show', name: 'Private show', price: 250, pricing: 'PER_GROUP'}];
  voteApi.getActivities.mockResolvedValue([
    {id: 'act1', name: 'River Boat Cruise', price: 40, extras: expectedExtras},
    {id: 'act2', name: 'Pub golf', price: 29},
  ]);
  const dispatch = jest.fn();

  renderSeeding(dispatch);

  await waitFor(() => expect(dispatch).toHaveBeenCalledWith(
    expect.objectContaining({type: 'SET_TRIP_ITEMS'})));
  const seeded = dispatch.mock.calls.find(([action]) => action.type === 'SET_TRIP_ITEMS')[0].tripItems;
  expect(seeded[0]).toMatchObject({id: 'act1', extras: expectedExtras});
  // An activity without extras still gets the list the cart code expects.
  expect(seeded[1]).toMatchObject({id: 'act2', extras: []});
});
