import { mockResult } from '../utils/mock'
import { getImagingStudies } from './store'

export function mockImagingStudies(params) {
  return mockResult({ list: getImagingStudies(params), page: 1, pageSize: 50 })
}
