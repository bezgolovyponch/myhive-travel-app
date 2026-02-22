import api from './api';

export class GoogleSheetsService {
    static async getStatus() {
        try {
            return await api.getGoogleSheetsStatus();
        } catch (error) {
            console.error('Error checking Google Sheets status:', error);
            throw error;
        }
    }

    static async exportTrip(tripData) {
        try {
            const exportRequest = this.formatTripForExport(tripData);
            const response = await api.exportTripToSheets(exportRequest);

            return response;
        } catch (error) {
            console.error('Error exporting trip to Google Sheets:', error);
            throw error;
        }
    }

    static async createSpreadsheet(title) {
        try {
            const response = await api.createSpreadsheet(title);

            if (response.success) {
                // Generate Google Sheets URL
                const sheetUrl = `https://docs.google.com/spreadsheets/d/${response.spreadsheetId}`;
                return {
                    ...response,
                    sheetUrl
                };
            }

            return response;
        } catch (error) {
            console.error('Error creating spreadsheet:', error);
            throw error;
        }
    }

    static async getSpreadsheetSheets(spreadsheetId) {
        try {
            return await api.getSpreadsheetSheets(spreadsheetId);
        } catch (error) {
            console.error('Error getting spreadsheet sheets:', error);
            throw error;
        }
    }

    static formatTripForExport(tripData) {
        const {tripName, userEmail, destinations, notes} = tripData;

        return {
            tripName: tripName || 'My Trip',
            userEmail: userEmail || '',
            destinations: destinations.map(dest => ({
                destinationName: dest.name || dest.destinationName || '',
                country: dest.country || '',
                duration: dest.duration || 1,
                startDate: dest.startDate || '',
                endDate: dest.endDate || '',
                activities: (dest.activities || []).map(activity => ({
                    activityName: activity.name || activity.activityName || '',
                    category: activity.category || '',
                    description: activity.description || '',
                    price: activity.price || 0,
                    duration: activity.duration || 0,
                    timeOfDay: activity.timeOfDay || ''
                }))
            })),
            notes: notes || ''
        };
    }
}

export default GoogleSheetsService;
