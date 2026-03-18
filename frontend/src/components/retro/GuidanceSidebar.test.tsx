import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { GuidanceSidebar } from './GuidanceSidebar'

describe('GuidanceSidebar', () => {
  it('renders the default title when no stepTitle provided', () => {
    render(<GuidanceSidebar />)
    expect(screen.getByText('Facilitator Guidance')).toBeInTheDocument()
  })

  it('renders a custom stepTitle when provided', () => {
    render(<GuidanceSidebar stepTitle="Mad Sad Glad" />)
    expect(screen.getByText('Mad Sad Glad')).toBeInTheDocument()
  })

  it('renders guidance text when guidance is provided', () => {
    render(<GuidanceSidebar guidance="Ask participants to reflect on the sprint." />)
    expect(screen.getByText('Ask participants to reflect on the sprint.')).toBeInTheDocument()
  })

  it('renders "No guidance available" message when guidance is null', () => {
    render(<GuidanceSidebar guidance={null} />)
    expect(screen.getByText('No guidance available for this step.')).toBeInTheDocument()
  })

  it('renders "No guidance available" message when guidance is undefined', () => {
    render(<GuidanceSidebar />)
    expect(screen.getByText('No guidance available for this step.')).toBeInTheDocument()
  })

  it('renders "No guidance available" message when guidance is empty string', () => {
    render(<GuidanceSidebar guidance="" />)
    expect(screen.getByText('No guidance available for this step.')).toBeInTheDocument()
  })

  it('renders bullet points for lines starting with dash', () => {
    render(<GuidanceSidebar guidance={`- First step\n- Second step`} />)
    expect(screen.getByText('First step')).toBeInTheDocument()
    expect(screen.getByText('Second step')).toBeInTheDocument()
  })

  it('is expanded by default', () => {
    render(<GuidanceSidebar guidance="Some guidance text" />)
    expect(screen.getByText('Some guidance text')).toBeVisible()
  })

  it('collapses when the toggle button is clicked', async () => {
    const user = userEvent.setup()
    render(<GuidanceSidebar guidance="Some guidance text" />)

    const toggleButton = screen.getByRole('button', { name: 'Collapse guidance' })
    await user.click(toggleButton)

    expect(screen.queryByText('Some guidance text')).not.toBeInTheDocument()
  })

  it('shows "Show guidance" button when collapsed', async () => {
    const user = userEvent.setup()
    render(<GuidanceSidebar guidance="Some guidance text" />)

    await user.click(screen.getByRole('button', { name: 'Collapse guidance' }))

    expect(screen.getByText('Show guidance ↓')).toBeInTheDocument()
  })

  it('expands again when "Show guidance" button is clicked', async () => {
    const user = userEvent.setup()
    render(<GuidanceSidebar guidance="Some guidance text" />)

    await user.click(screen.getByRole('button', { name: 'Collapse guidance' }))
    expect(screen.queryByText('Some guidance text')).not.toBeInTheDocument()

    await user.click(screen.getByText('Show guidance ↓'))
    expect(screen.getByText('Some guidance text')).toBeInTheDocument()
  })

  it('toggle button has correct aria-expanded attribute', async () => {
    const user = userEvent.setup()
    render(<GuidanceSidebar />)

    const toggleButton = screen.getByRole('button', { name: 'Collapse guidance' })
    expect(toggleButton).toHaveAttribute('aria-expanded', 'true')

    await user.click(toggleButton)
    expect(screen.getByRole('button', { name: 'Expand guidance' })).toHaveAttribute('aria-expanded', 'false')
  })
})
