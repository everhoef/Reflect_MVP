import { useAssistantStore } from '@/modules/facilitation/store/assistantStore'
import { HISTORY_MAX_SIZE } from '@/shared/types/assistantState'

const RETRO_ID = 'retro-uuid-test'

beforeEach(() => {
  useAssistantStore.setState({
    current: null,
    history: [],
    facilitatorCoachingNote: null,
  })
})

describe('assistantStore — pushMessage', () => {
  it('empty store has null current and empty history', () => {
    const state = useAssistantStore.getState()
    expect(state.current).toBeNull()
    expect(state.history).toHaveLength(0)
  })

  it('first push becomes current with no history', () => {
    useAssistantStore.getState().pushMessage(RETRO_ID, 1, 'Step One', 'Welcome to the retrospective.')

    const state = useAssistantStore.getState()
    expect(state.current).not.toBeNull()
    expect(state.current?.publicText).toBe('Welcome to the retrospective.')
    expect(state.current?.stepId).toBe(1)
    expect(state.history).toHaveLength(0)
  })

  it('second push moves first message to history', () => {
    useAssistantStore.getState().pushMessage(RETRO_ID, 1, 'Step One', 'First message.')
    useAssistantStore.getState().pushMessage(RETRO_ID, 2, 'Step Two', 'Second message.')

    const state = useAssistantStore.getState()
    expect(state.current?.stepId).toBe(2)
    expect(state.history).toHaveLength(1)
    expect(state.history.at(0)?.stepId).toBe(1)
  })

  it('four pushes — history holds exactly three, newest-first', () => {
    for (let i = 1; i <= 4; i++) {
      useAssistantStore.getState().pushMessage(RETRO_ID, i, `Step ${i}`, `Msg ${i}`)
    }

    const state = useAssistantStore.getState()
    expect(state.current?.stepId).toBe(4)
    expect(state.history).toHaveLength(3)
    expect(state.history.at(0)?.stepId).toBe(3)
    expect(state.history.at(1)?.stepId).toBe(2)
    expect(state.history.at(2)?.stepId).toBe(1)
  })

  it('five pushes — oldest is dropped, cap stays at three', () => {
    for (let i = 1; i <= 5; i++) {
      useAssistantStore.getState().pushMessage(RETRO_ID, i, `Step ${i}`, `Msg ${i}`)
    }

    const state = useAssistantStore.getState()
    expect(state.current?.stepId).toBe(5)
    expect(state.history).toHaveLength(3)
    expect(state.history.at(0)?.stepId).toBe(4)
    expect(state.history.at(1)?.stepId).toBe(3)
    expect(state.history.at(2)?.stepId).toBe(2)
  })

  it('ten pushes — cap never exceeds HISTORY_MAX_SIZE', () => {
    for (let i = 1; i <= 10; i++) {
      useAssistantStore.getState().pushMessage(RETRO_ID, i, `Step ${i}`, `Msg ${i}`)
    }

    const state = useAssistantStore.getState()
    expect(state.history.length).toBeLessThanOrEqual(HISTORY_MAX_SIZE)
  })

  it('retroId is stored on current message', () => {
    useAssistantStore.getState().pushMessage(RETRO_ID, 1, 'Step One', 'Msg 1')
    useAssistantStore.getState().pushMessage(RETRO_ID, 2, 'Step Two', 'Msg 2')

    const state = useAssistantStore.getState()
    expect(state.current?.retroId).toBe(RETRO_ID)
    expect(state.history.at(0)?.retroId).toBe(RETRO_ID)
  })
})

describe('assistantStore — coaching note', () => {
  it('setCoachingNote stores the note', () => {
    useAssistantStore.getState().setCoachingNote('Private tip for facilitator.')

    expect(useAssistantStore.getState().facilitatorCoachingNote).toBe('Private tip for facilitator.')
  })

  it('setCoachingNote(null) clears the note', () => {
    useAssistantStore.getState().setCoachingNote('Some note.')
    useAssistantStore.getState().setCoachingNote(null)

    expect(useAssistantStore.getState().facilitatorCoachingNote).toBeNull()
  })
})

describe('assistantStore — bootstrapState', () => {
  it('bootstrapState hydrates the full state', () => {
    const bootstrapped = {
      current: { retroId: RETRO_ID, stepId: 5, stepTitle: 'Step Five', publicText: 'Current' },
      history: [
        { retroId: RETRO_ID, stepId: 4, stepTitle: 'Step Four', publicText: 'Prev 1' },
        { retroId: RETRO_ID, stepId: 3, stepTitle: 'Step Three', publicText: 'Prev 2' },
      ],
      facilitatorCoachingNote: 'Coach tip',
    }

    useAssistantStore.getState().bootstrapState(bootstrapped)

    const state = useAssistantStore.getState()
    expect(state.current?.stepId).toBe(5)
    expect(state.history).toHaveLength(2)
    expect(state.facilitatorCoachingNote).toBe('Coach tip')
  })
})

describe('assistantStore — clearAssistant', () => {
  it('clearAssistant resets everything to initial state', () => {
    useAssistantStore.getState().pushMessage(RETRO_ID, 1, 'Step One', 'Msg 1')
    useAssistantStore.getState().setCoachingNote('Some note')

    useAssistantStore.getState().clearAssistant()

    const state = useAssistantStore.getState()
    expect(state.current).toBeNull()
    expect(state.history).toHaveLength(0)
    expect(state.facilitatorCoachingNote).toBeNull()
  })
})
