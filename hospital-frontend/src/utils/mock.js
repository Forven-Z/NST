/** @returns {boolean} */
export function useMock() {
  return import.meta.env.VITE_USE_MOCK === 'true'
}

export function mockResult(data, message = 'ok') {
  return Promise.resolve({
    code: 200,
    message,
    success: true,
    data,
  })
}
