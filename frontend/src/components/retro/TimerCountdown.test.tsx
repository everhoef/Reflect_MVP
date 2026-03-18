import { render, screen, act } from '@testing-library/react'
import { TimerCountdown } from './TimerCountdown'
import { useRetroStateStore } from '@/store/retroStore'

function setTimerInactive() {
  useRetroStateStore.setState({
    remainingSeconds: null,
    isPaused: false,
    timerState: null,
  })
}

function setTimerActive(remainingSeconds: number) {
  useRetroStateStore.setState({
    remainingSeconds,
    isPaused: false,
    timerState: 'green',
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

  it('shows "Paused" indicator when isPaused is true', () => {
    act(() => {
      useRetroStateStore.setState({
        remainingSeconds: 45,
        isPaused: true,
        timerState: 'yellow',
      })
    })
    render(<TimerCountdown durationSeconds={90} />)
    expect(screen.getByText(/Paused/)).toBeInTheDocument()
  })

  it("shows \"Time's up!\" when remaining time reaches zero", () => {
    act(() => {
      setTimerActive(0)
    })
    render(<TimerCountdown durationSeconds={60} />)
    expect(screen.getByText("Time's up!")).toBeInTheDocument()
  })

  it('renders 01:30 for a 90-second timer just started', () => {
    act(() => {
      setTimerActive(90)
    })
    render(<TimerCountdown durationSeconds={90} />)
    expect(screen.getByText('01:30')).toBeInTheDocument()
  })
})
