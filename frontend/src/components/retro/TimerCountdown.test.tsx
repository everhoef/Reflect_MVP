import { render, screen, act } from '@testing-library/react'
import { TimerCountdown } from './TimerCountdown'
import { useRetroStateStore } from '@/store/retroStore'

function setTimerInactive() {
  useRetroStateStore.setState({
    timerActive: false,
    timerStartedAt: null,
    timerDurationSeconds: null,
  })
}

function setTimerActive(durationSeconds: number) {
  useRetroStateStore.setState({
    timerActive: true,
    timerStartedAt: Date.now(),
    timerDurationSeconds: durationSeconds,
  })
}

beforeEach(() => {
  setTimerInactive()
})

describe('TimerCountdown', () => {
  it('shows "Timer not started" when timer is inactive', () => {
    render(<TimerCountdown durationSeconds={120} />)
    expect(screen.getByText('Timer not started')).toBeInTheDocument()
  })

  it('shows formatted MM:SS when timer is active', () => {
    act(() => {
      setTimerActive(90)
    })
    render(<TimerCountdown durationSeconds={90} />)
    expect(screen.getByText(/\d{2}:\d{2}/)).toBeInTheDocument()
  })

  it('shows "Time\'s up!" when remaining time is zero', () => {
    act(() => {
      useRetroStateStore.setState({
        timerActive: true,
        timerStartedAt: Date.now() - 120_000,
        timerDurationSeconds: 60,
      })
    })
    render(<TimerCountdown durationSeconds={60} />)
    expect(screen.getByText("Time's up!")).toBeInTheDocument()
  })

  it('renders 02:00 for a 120-second timer just started', () => {
    act(() => {
      setTimerActive(120)
    })
    render(<TimerCountdown durationSeconds={120} />)
    expect(screen.getByText('02:00')).toBeInTheDocument()
  })
})
