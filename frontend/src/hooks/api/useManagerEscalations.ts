import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../lib/api-client'
import type { components } from '../../types/api'

export type EscalatedItem = components['schemas']['EscalatedItemDto']

export function useManagerEscalations(enabled: boolean = true) {
  return useQuery({
    queryKey: ['manager', 'escalations'],
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/api/manager/escalations')
      if (error) {
        throw new Error('Failed to fetch manager escalations')
      }
      return data
    },
    enabled,
  })
}
