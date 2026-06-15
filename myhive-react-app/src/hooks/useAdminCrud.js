import {useCallback, useEffect, useState} from 'react';
import {useAdminApi} from './useAdminApi';
import {useAuthErrorHandler} from './useAuthErrorHandler';

export function useAdminCrud({
                                 emptyForm,
                                 fetchFn,
                                 createFn,
                                 updateFn,
                                 deleteFn,
                                 mapItemToForm,
                                 buildPayload = (form) => form,
                                 mapDeleteError,
                                 pageSize = 10,
                             }) {
    const adminApi = useAdminApi();
    const handleAuthError = useAuthErrorHandler();

    const [items, setItems] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);

    const [showModal, setShowModal] = useState(false);
    const [editing, setEditing] = useState(null);
    const [form, setForm] = useState(emptyForm);
    const [saving, setSaving] = useState(false);
    // Save failures are rendered inside the modal (the page-level error alert
    // sits behind the open modal's backdrop and is invisible there).
    const [saveError, setSaveError] = useState('');

    const [deleteId, setDeleteId] = useState(null);
    const [uploading, setUploading] = useState(false);

    const fetchData = useCallback(async () => {
        try {
            setLoading(true);
            setError('');
            const response = await fetchFn(adminApi, page, pageSize);
            setItems(response.content);
            setTotalPages(response.totalPages);
            setTotalElements(response.totalElements);
        } catch (err) {
            if (handleAuthError(err)) return;
            setError(err.message || 'Failed to load data.');
        } finally {
            setLoading(false);
        }
    }, [adminApi, handleAuthError, page, pageSize, fetchFn]);

    useEffect(() => {
        fetchData();
    }, [fetchData]);

    const openCreate = () => {
        setSaveError('');
        setForm(emptyForm);
        setEditing(null);
        setShowModal(true);
    };

    const openEdit = (item) => {
        setSaveError('');
        setForm(mapItemToForm(item));
        setEditing(item);
        setShowModal(true);
    };

    const handleSave = async () => {
        try {
            setSaving(true);
            setSaveError('');
            const payload = buildPayload(form);
            if (editing) {
                await updateFn(adminApi, editing.id, payload);
            } else {
                await createFn(adminApi, payload);
            }
            setShowModal(false);
            await fetchData();
        } catch (err) {
            if (handleAuthError(err)) return;
            setSaveError(err.message || 'Failed to save.');
        } finally {
            setSaving(false);
        }
    };

    const handleImageUpload = async (file) => {
        setUploading(true);
        setSaveError('');
        try {
            const {url} = await adminApi.uploadImage(file);
            setForm(prev => ({...prev, imageUrl: url}));
        } finally {
            setUploading(false);
        }
    };

    const handleImageUploadError = (err) => {
        setSaveError(err.message || 'Failed to upload image');
    };

    const handleDelete = async () => {
        try {
            setSaving(true);
            setError('');
            await deleteFn(adminApi, deleteId);
            await fetchData();
        } catch (err) {
            if (handleAuthError(err)) return;
            // mapDeleteError lets a screen turn a structured failure (e.g. a 409
            // listing blocking references) into a friendly message.
            setError(mapDeleteError ? mapDeleteError(err) : (err.message || 'Failed to delete.'));
        } finally {
            setDeleteId(null);
            setSaving(false);
        }
    };

    return {
        items,
        loading,
        error,
        setError,
        page,
        setPage,
        totalPages,
        totalElements,
        showModal,
        setShowModal,
        editing,
        form,
        setForm,
        saving,
        setSaving,
        saveError,
        setSaveError,
        uploading,
        setUploading,
        handleImageUpload,
        handleImageUploadError,
        deleteId,
        setDeleteId,
        fetchData,
        openCreate,
        openEdit,
        handleSave,
        handleDelete,
        adminApi,
    };
}
