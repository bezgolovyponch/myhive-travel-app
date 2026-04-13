import {Form, Spinner} from 'react-bootstrap';

function ImageUploadField({imageUrl, uploading, onUpload, onError}) {
    return (
        <Form.Group className="mb-3">
            <Form.Label className="small fw-semibold text-white">Image</Form.Label>
            <Form.Control
                type="file"
                accept="image/*"
                disabled={uploading}
                onChange={async (e) => {
                    const file = e.target.files[0];
                    if (!file) return;
                    try {
                        await onUpload(file);
                    } catch (err) {
                        onError(err);
                    }
                }}
            />
            {uploading && (
                <div className="mt-2">
                    <Spinner animation="border" size="sm"/>{' '}
                    <span className="small text-muted">Uploading...</span>
                </div>
            )}
            {imageUrl && !uploading && (
                <div className="mt-2">
                    <img
                        src={imageUrl}
                        alt="Preview"
                        style={{maxHeight: 120, borderRadius: 6, objectFit: 'cover'}}
                    />
                    <div className="small text-muted mt-1 text-truncate" style={{maxWidth: 300}}>
                        {imageUrl}
                    </div>
                </div>
            )}
        </Form.Group>
    );
}

export default ImageUploadField;
