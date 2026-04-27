import {useState} from 'react';
import {Button, Form, Modal} from 'react-bootstrap';
import {DEFAULT_ACTIVITY_IMAGE, formatAmount} from '../../utils/format';
import {
  DndContext,
  closestCenter,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
} from '@dnd-kit/core';
import {
  SortableContext,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy,
  useSortable,
  arrayMove,
} from '@dnd-kit/sortable';
import {CSS} from '@dnd-kit/utilities';
import './PackageActivityPicker.css';

function SortableRow({item, onRemove}) {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({id: item.activityId});

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
  };

  return (
    <div
      ref={setNodeRef}
      style={style}
      className="package-activity-picker__row"
    >
      <span
        className="package-activity-picker__handle"
        {...attributes}
        {...listeners}
      >
        ☰
      </span>
      <img
        src={item.imageUrl || DEFAULT_ACTIVITY_IMAGE}
        alt={item.name}
        className="package-activity-picker__thumb"
      />
      <div className="package-activity-picker__info">
        <span className="fw-bold">{item.name}</span>
        {item.price != null && (
          <span className="text-muted small d-block">{formatAmount(item.price)}</span>
        )}
      </div>
      {item.duration != null && (
        <span className="text-muted small">{item.duration}</span>
      )}
      <button
        type="button"
        className="package-activity-picker__remove ms-auto"
        onClick={() => onRemove(item.activityId)}
        aria-label={`Remove ${item.name}`}
      >
        ✕
      </button>
    </div>
  );
}

function AddActivityModal({show, onHide, availableActivities, selectedIds, onAdd}) {
  const [search, setSearch] = useState('');

  const filtered = availableActivities.filter((a) => {
    if (selectedIds.has(a.id)) {
      return false;
    }
    if (!search.trim()) {
      return true;
    }
    return a.name.toLowerCase().includes(search.trim().toLowerCase());
  });

  const handleAdd = (activity) => {
    onAdd(activity);
    onHide();
  };

  const handleHide = () => {
    setSearch('');
    onHide();
  };

  return (
    <Modal show={show} onHide={handleHide} centered>
      <Modal.Header closeButton>
        <Modal.Title className="fs-5">Add activity</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        <Form.Control
          type="text"
          placeholder="Search activities…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="mb-3"
          autoFocus
        />
        {filtered.length === 0 && (
          <p className="text-muted text-center small mb-0">No activities found.</p>
        )}
        {filtered.map((activity) => (
          <div key={activity.id} className="package-activity-picker__modal-row">
            <img
              src={activity.imageUrl || DEFAULT_ACTIVITY_IMAGE}
              alt={activity.name}
              className="package-activity-picker__thumb"
            />
            <div className="package-activity-picker__info">
              <span className="fw-bold">{activity.name}</span>
              {activity.price != null && (
                <span className="text-muted small d-block">{formatAmount(activity.price)}</span>
              )}
            </div>
            <Button
              size="sm"
              variant="outline-primary"
              className="ms-auto"
              onClick={() => handleAdd(activity)}
            >
              + Add
            </Button>
          </div>
        ))}
      </Modal.Body>
      <Modal.Footer>
        <Button variant="outline-secondary" onClick={handleHide}>Cancel</Button>
      </Modal.Footer>
    </Modal>
  );
}

function reindex(items) {
  return items.map((item, i) => ({...item, position: i}));
}

function PackageActivityPicker({value, onChange, availableActivities, disabled}) {
  const [showModal, setShowModal] = useState(false);

  const sensors = useSensors(
    useSensor(PointerSensor),
    useSensor(KeyboardSensor, {coordinateGetter: sortableKeyboardCoordinates}),
  );

  const selectedIds = new Set((value || []).map((item) => item.activityId));

  const handleDragEnd = (event) => {
    const {active, over} = event;
    if (!over || active.id === over.id) {
      return;
    }
    const oldIndex = value.findIndex((item) => item.activityId === active.id);
    const newIndex = value.findIndex((item) => item.activityId === over.id);
    onChange(reindex(arrayMove(value, oldIndex, newIndex)));
  };

  const handleRemove = (activityId) => {
    onChange(reindex((value || []).filter((item) => item.activityId !== activityId)));
  };

  const handleAdd = (activity) => {
    const next = [
      ...(value || []),
      {
        activityId: activity.id,
        name: activity.name,
        price: activity.price,
        imageUrl: activity.imageUrl,
        duration: activity.duration,
      },
    ];
    onChange(reindex(next));
  };

  const items = value || [];
  const ids = items.map((item) => item.activityId);

  return (
    <div className="package-activity-picker">
      <div className="package-activity-picker__header">
        <span className="fw-semibold">Activities ({items.length})</span>
        {!disabled && (
          <Button
            size="sm"
            variant="outline-primary"
            onClick={() => setShowModal(true)}
          >
            + Add activity
          </Button>
        )}
      </div>

      {disabled && (
        <div className="package-activity-picker__empty">
          Select a destination first
        </div>
      )}

      {!disabled && items.length === 0 && (
        <div className="package-activity-picker__empty">
          No activities yet
        </div>
      )}

      {!disabled && items.length > 0 && (
        <DndContext
          sensors={sensors}
          collisionDetection={closestCenter}
          onDragEnd={handleDragEnd}
        >
          <SortableContext items={ids} strategy={verticalListSortingStrategy}>
            {items.map((item) => (
              <SortableRow
                key={item.activityId}
                item={item}
                onRemove={handleRemove}
              />
            ))}
          </SortableContext>
        </DndContext>
      )}

      <AddActivityModal
        show={showModal}
        onHide={() => setShowModal(false)}
        availableActivities={availableActivities || []}
        selectedIds={selectedIds}
        onAdd={handleAdd}
      />
    </div>
  );
}

export default PackageActivityPicker;
