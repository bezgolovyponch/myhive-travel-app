import {API_BASE_URL} from './config';
import {parseApiError} from './httpError';

// The vote managerToken authorizes payment actions; the voteShareToken identifies the vote. Both are
// sent as request headers (not query params) so they are not captured in CDN/proxy/app access logs (M5).
export const paymentApi = {
    async createDepositSession(voteShareToken, managerToken, tripData) {
        const url = `${API_BASE_URL}/payments/deposit-session`;
        const response = await fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-Vote-Share-Token': voteShareToken,
                'X-Manager-Token': managerToken,
            },
            body: JSON.stringify(tripData),
        });
        if (!response.ok) throw await parseApiError(response, 'Failed to start payment');
        return response.json();
    },

    async createConsultationLead(voteShareToken, managerToken, tripData) {
        const url = `${API_BASE_URL}/payments/consultation-lead`;
        const response = await fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-Vote-Share-Token': voteShareToken,
                'X-Manager-Token': managerToken,
            },
            body: JSON.stringify(tripData),
        });
        if (!response.ok) throw await parseApiError(response, 'Failed to submit consultation request');
        return response.json();
    },
};

export default paymentApi;
