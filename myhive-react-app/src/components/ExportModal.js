import React from 'react';

function ExportModal({isOpen, onClose, onConfirm, isExporting, exportError}) {
    if (!isOpen) return null;

    return (
        <div className="modal-overlay">
            <div className="modal-content">
                <div className="modal-header">
                    <h3>Export to Google Sheets</h3>
                    <button className="modal-close" onClick={onClose}>×</button>
                </div>

                <div className="modal-body">
                    <p>Export your trip itinerary to Google Sheets for easy sharing and collaboration.</p>

                    {exportError && (
                        <div className="export-error">
                            <p>{exportError}</p>
                        </div>
                    )}

                    <div className="export-info">
                        <h4>What will be exported:</h4>
                        <ul>
                            <li>Trip name and details</li>
                            <li>Selected destinations</li>
                            <li>Activities with prices and descriptions</li>
                            <li>Trip notes</li>
                        </ul>
                    </div>
                </div>

                <div className="modal-footer">
                    <button
                        className="btn btn--secondary"
                        onClick={onClose}
                        disabled={isExporting}
                    >
                        Cancel
                    </button>
                    <button
                        className="btn btn--primary"
                        onClick={onConfirm}
                        disabled={isExporting}
                    >
                        {isExporting ? 'Exporting...' : 'Export to Google Sheets'}
                    </button>
                </div>
            </div>
        </div>
    );
}

export default ExportModal;
