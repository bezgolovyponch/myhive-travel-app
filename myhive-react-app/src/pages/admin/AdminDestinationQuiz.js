import {useEffect, useState} from 'react';
import {Alert, Button, Card, Form, Spinner} from 'react-bootstrap';
import {useAdminApi} from '../../hooks/useAdminApi';
import './AdminDestinationQuiz.css';

let tempKeyCounter = 0;
const nextTempKey = () => `tmp-${++tempKeyCounter}`;

function attachKeys(quiz) {
    return {
        questions: (quiz.questions || []).map(question => ({
            ...question,
            _key: question.id || nextTempKey(),
            answers: (question.answers || []).map(answer => ({
                ...answer,
                _key: answer.id || nextTempKey(),
                weights: (answer.weights || []).map(weight => ({
                    ...weight,
                    _key: nextTempKey(),
                })),
            })),
        })),
    };
}

function AdminDestinationQuiz({destinationId}) {
    const adminApi = useAdminApi();
    const [quiz, setQuiz] = useState(null);
    const [categories, setCategories] = useState([]);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState(null);
    const [successMsg, setSuccessMsg] = useState(null);

    useEffect(() => {
        let cancelled = false;
        setLoading(true);
        Promise.all([
            adminApi.getDestinationQuiz(destinationId),
            adminApi.getCategories(),
        ]).then(([q, cats]) => {
            if (cancelled) {
                return;
            }
            setQuiz(attachKeys(q));
            setCategories(cats);
            setLoading(false);
        }).catch(e => {
            if (cancelled) {
                return;
            }
            setError(e.message || 'Failed to load quiz');
            setLoading(false);
        });
        return () => {
            cancelled = true;
        };
    }, [adminApi, destinationId]);

    const setQuestions = (mapper) => {
        setQuiz(prev => ({questions: mapper(prev.questions)}));
    };

    const updateQuestion = (qKey, patch) => {
        setQuestions(qs => qs.map(q => q._key === qKey ? {...q, ...patch} : q));
    };

    const addQuestion = () => {
        setQuestions(qs => [
            ...qs,
            {
                _key: nextTempKey(),
                id: null,
                prompt: '',
                sortOrder: qs.length,
                answers: [],
            },
        ]);
    };

    const deleteQuestion = (qKey) => {
        setQuestions(qs => qs.filter(q => q._key !== qKey));
    };

    const addAnswer = (qKey) => {
        setQuestions(qs => qs.map(q => q._key === qKey ? {
            ...q,
            answers: [
                ...q.answers,
                {
                    _key: nextTempKey(),
                    id: null,
                    label: '',
                    sortOrder: q.answers.length,
                    weights: [],
                },
            ],
        } : q));
    };

    const updateAnswer = (qKey, aKey, patch) => {
        setQuestions(qs => qs.map(q => q._key === qKey ? {
            ...q,
            answers: q.answers.map(a => a._key === aKey ? {...a, ...patch} : a),
        } : q));
    };

    const deleteAnswer = (qKey, aKey) => {
        setQuestions(qs => qs.map(q => q._key === qKey ? {
            ...q,
            answers: q.answers.filter(a => a._key !== aKey),
        } : q));
    };

    const addWeight = (qKey, aKey) => {
        const defaultCategoryId = categories.length > 0 ? categories[0].id : null;
        setQuestions(qs => qs.map(q => q._key === qKey ? {
            ...q,
            answers: q.answers.map(a => a._key === aKey ? {
                ...a,
                weights: [
                    ...a.weights,
                    {_key: nextTempKey(), categoryId: defaultCategoryId, weight: 0},
                ],
            } : a),
        } : q));
    };

    const updateWeight = (qKey, aKey, wKey, patch) => {
        setQuestions(qs => qs.map(q => q._key === qKey ? {
            ...q,
            answers: q.answers.map(a => a._key === aKey ? {
                ...a,
                weights: a.weights.map(w => w._key === wKey ? {...w, ...patch} : w),
            } : a),
        } : q));
    };

    const deleteWeight = (qKey, aKey, wKey) => {
        setQuestions(qs => qs.map(q => q._key === qKey ? {
            ...q,
            answers: q.answers.map(a => a._key === aKey ? {
                ...a,
                weights: a.weights.filter(w => w._key !== wKey),
            } : a),
        } : q));
    };

    const validate = () => {
        for (const q of quiz.questions) {
            if (!q.prompt || !q.prompt.trim()) {
                return 'Every question needs a prompt.';
            }
            if (!q.answers || q.answers.length === 0) {
                return `Question "${q.prompt}" needs at least one answer.`;
            }
            for (const a of q.answers) {
                if (!a.label || !a.label.trim()) {
                    return `Every answer for "${q.prompt}" needs a label.`;
                }
                for (const w of (a.weights || [])) {
                    if (!w.categoryId) {
                        return `Weights for "${a.label}" must reference a category.`;
                    }
                }
            }
        }
        return null;
    };

    const handleSave = async () => {
        setError(null);
        setSuccessMsg(null);
        const validationError = validate();
        if (validationError) {
            setError(validationError);
            return;
        }
        setSaving(true);
        try {
            const payload = {
                questions: quiz.questions.map(q => ({
                    id: q.id || null,
                    prompt: q.prompt,
                    sortOrder: Number(q.sortOrder) || 0,
                    answers: q.answers.map(a => ({
                        id: a.id || null,
                        label: a.label,
                        sortOrder: Number(a.sortOrder) || 0,
                        weights: (a.weights || []).map(w => ({
                            categoryId: w.categoryId,
                            weight: Number(w.weight) || 0,
                        })),
                    })),
                })),
            };
            const result = await adminApi.putDestinationQuiz(destinationId, payload);
            setQuiz(attachKeys(result));
            setSuccessMsg('Quiz saved.');
        } catch (e) {
            setError(e.message || 'Failed to save quiz');
        } finally {
            setSaving(false);
        }
    };

    if (loading) {
        return (
            <div className="quiz-admin-loading">
                <Spinner animation="border" size="sm"/> Loading quiz…
            </div>
        );
    }

    if (!quiz) {
        return <Alert variant="danger">{error || 'Failed to load quiz'}</Alert>;
    }

    return (
        <div className="quiz-admin">
            <div className="d-flex align-items-center justify-content-between mb-3">
                <h6 className="fw-semibold mb-0">Quiz editor</h6>
                <div className="d-flex gap-2">
                    <Button variant="outline-secondary" size="sm" onClick={addQuestion}>
                        + Add question
                    </Button>
                    <Button variant="primary" size="sm" onClick={handleSave} disabled={saving}>
                        {saving ? <Spinner animation="border" size="sm"/> : 'Save quiz'}
                    </Button>
                </div>
            </div>

            {error && (
                <Alert variant="danger" dismissible onClose={() => setError(null)}>{error}</Alert>
            )}
            {successMsg && (
                <Alert variant="success" dismissible onClose={() => setSuccessMsg(null)}>{successMsg}</Alert>
            )}

            {quiz.questions.length === 0 && (
                <p className="text-muted small mb-3">
                    No questions yet. Add one to start building the quiz.
                </p>
            )}

            {quiz.questions.map((question) => (
                <Card key={question._key} className="quiz-question-card mb-3">
                    <Card.Body>
                        <div className="d-flex gap-2 align-items-start mb-3">
                            <Form.Group className="flex-grow-1">
                                <Form.Label className="small fw-semibold">Prompt</Form.Label>
                                <Form.Control
                                    value={question.prompt}
                                    onChange={e => updateQuestion(question._key, {prompt: e.target.value})}
                                    placeholder="Question text"
                                />
                            </Form.Group>
                            <Form.Group style={{width: 110}}>
                                <Form.Label className="small fw-semibold">Sort</Form.Label>
                                <Form.Control
                                    type="number"
                                    value={question.sortOrder}
                                    onChange={e => updateQuestion(question._key, {sortOrder: e.target.value})}
                                />
                            </Form.Group>
                            <Button
                                variant="outline-danger"
                                size="sm"
                                style={{marginTop: 28}}
                                onClick={() => deleteQuestion(question._key)}
                            >
                                Delete question
                            </Button>
                        </div>

                        <div className="quiz-answers">
                            {question.answers.map((answer) => (
                                <div key={answer._key} className="quiz-answer-block">
                                    <div className="d-flex gap-2 align-items-start mb-2">
                                        <Form.Group className="flex-grow-1">
                                            <Form.Label className="small fw-semibold">Answer label</Form.Label>
                                            <Form.Control
                                                value={answer.label}
                                                onChange={e => updateAnswer(question._key, answer._key, {label: e.target.value})}
                                                placeholder="Answer text"
                                            />
                                        </Form.Group>
                                        <Form.Group style={{width: 110}}>
                                            <Form.Label className="small fw-semibold">Sort</Form.Label>
                                            <Form.Control
                                                type="number"
                                                value={answer.sortOrder}
                                                onChange={e => updateAnswer(question._key, answer._key, {sortOrder: e.target.value})}
                                            />
                                        </Form.Group>
                                        <Button
                                            variant="outline-danger"
                                            size="sm"
                                            style={{marginTop: 28}}
                                            onClick={() => deleteAnswer(question._key, answer._key)}
                                        >
                                            Delete answer
                                        </Button>
                                    </div>

                                    <div className="quiz-weights">
                                        <div className="small fw-semibold mb-1">Category weights</div>
                                        {(answer.weights || []).length === 0 && (
                                            <div className="text-muted small mb-2">No weights configured.</div>
                                        )}
                                        {(answer.weights || []).map((weight) => (
                                            <div key={weight._key} className="d-flex gap-2 align-items-center mb-2">
                                                <Form.Select
                                                    value={weight.categoryId || ''}
                                                    onChange={e => updateWeight(
                                                        question._key, answer._key, weight._key,
                                                        {categoryId: e.target.value || null}
                                                    )}
                                                    style={{maxWidth: 260}}
                                                >
                                                    <option value="">— select category —</option>
                                                    {categories.map(cat => (
                                                        <option key={cat.id} value={cat.id}>{cat.name}</option>
                                                    ))}
                                                </Form.Select>
                                                <Form.Control
                                                    type="number"
                                                    value={weight.weight}
                                                    onChange={e => updateWeight(
                                                        question._key, answer._key, weight._key,
                                                        {weight: e.target.value}
                                                    )}
                                                    style={{maxWidth: 120}}
                                                />
                                                <Button
                                                    variant="outline-danger"
                                                    size="sm"
                                                    onClick={() => deleteWeight(question._key, answer._key, weight._key)}
                                                >
                                                    Remove
                                                </Button>
                                            </div>
                                        ))}
                                        <Button
                                            variant="outline-secondary"
                                            size="sm"
                                            onClick={() => addWeight(question._key, answer._key)}
                                        >
                                            + Add weight
                                        </Button>
                                    </div>
                                </div>
                            ))}
                            <Button
                                variant="outline-secondary"
                                size="sm"
                                className="mt-2"
                                onClick={() => addAnswer(question._key)}
                            >
                                + Add answer
                            </Button>
                        </div>
                    </Card.Body>
                </Card>
            ))}

            <div className="d-flex justify-content-end gap-2 mt-3">
                <Button variant="outline-secondary" size="sm" onClick={addQuestion}>
                    + Add question
                </Button>
                <Button variant="primary" size="sm" onClick={handleSave} disabled={saving}>
                    {saving ? <Spinner animation="border" size="sm"/> : 'Save quiz'}
                </Button>
            </div>
        </div>
    );
}

export default AdminDestinationQuiz;
