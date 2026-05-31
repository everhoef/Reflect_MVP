import { describe, it, expect } from 'vitest'
import { retroPhaseToStageIndex, STAGE_LABELS } from '@/modules/facilitation/lib/retroPhaseToStageIndex'

describe('retroPhaseToStageIndex', () => {
  it('maps SET_THE_STAGE → 1', () => {
    expect(retroPhaseToStageIndex('SET_THE_STAGE')).toBe(1)
  })
  it('maps GATHER_DATA → 2', () => {
    expect(retroPhaseToStageIndex('GATHER_DATA')).toBe(2)
  })
  it('maps GENERATE_INSIGHTS → 3', () => {
    expect(retroPhaseToStageIndex('GENERATE_INSIGHTS')).toBe(3)
  })
  it('maps DECIDE_ACTIONS → 4', () => {
    expect(retroPhaseToStageIndex('DECIDE_ACTIONS')).toBe(4)
  })
  it('maps CLOSE_RETRO → 5', () => {
    expect(retroPhaseToStageIndex('CLOSE_RETRO')).toBe(5)
  })
  it('maps LOBBY → 0', () => {
    expect(retroPhaseToStageIndex('LOBBY')).toBe(0)
  })
  it('maps COMPLETED → 0', () => {
    expect(retroPhaseToStageIndex('COMPLETED')).toBe(0)
  })
  it('maps unknown string → 0', () => {
    expect(retroPhaseToStageIndex('UNKNOWN_PHASE')).toBe(0)
  })
  it('maps null → 0', () => {
    expect(retroPhaseToStageIndex(null)).toBe(0)
  })
  it('maps undefined → 0', () => {
    expect(retroPhaseToStageIndex(undefined)).toBe(0)
  })

  describe('STAGE_LABELS', () => {
    it('has exactly 5 entries', () => {
      expect(STAGE_LABELS).toHaveLength(5)
    })
    it('STAGE_LABELS[0] is "Set the Stage"', () => {
      expect(STAGE_LABELS[0]).toBe('Set the Stage')
    })
  })
})
