import { useRetroStateStore } from '@/modules/facilitation/store/retroStore'

beforeEach(() => {
  useRetroStateStore.setState({
    remainingSeconds: null,
    isPaused: false,
    timerState: null,
  })
})

describe('retroStore — timer actions', () => {
  it('setTimerState stores backend payload correctly', () => {
    useRetroStateStore.getState().setTimerState({
      remainingSeconds: 90,
      isPaused: false,
      timerState: 'yellow',
    })

    const state = useRetroStateStore.getState()
    expect(state.remainingSeconds).toBe(90)
    expect(state.isPaused).toBe(false)
    expect(state.timerState).toBe('yellow')
  })

  it('clearTimer resets all timer fields to null/false', () => {
    useRetroStateStore.setState({
      remainingSeconds: 60,
      isPaused: false,
      timerState: 'green',
    })

    useRetroStateStore.getState().clearTimer()

    const state = useRetroStateStore.getState()
    expect(state.remainingSeconds).toBeNull()
    expect(state.isPaused).toBe(false)
    expect(state.timerState).toBeNull()
  })

  it('setTimerState with isPaused=true stores paused state correctly', () => {
    useRetroStateStore.getState().setTimerState({
      remainingSeconds: 45,
      isPaused: true,
      timerState: 'red',
    })

    const state = useRetroStateStore.getState()
    expect(state.remainingSeconds).toBe(45)
    expect(state.isPaused).toBe(true)
    expect(state.timerState).toBe('red')
  })
})
