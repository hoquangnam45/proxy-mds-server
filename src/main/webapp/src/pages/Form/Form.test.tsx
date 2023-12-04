import React from 'react'
import { render, screen } from '@testing-library/react'
import '@testing-library/jest-dom/extend-expect'
import Form from './Form'

describe('<Form />', () => {
  test('it should mount', () => {
    // TODO: Setup test schema with schemaName: test and subSchemaName: test
    render(<Form schemaName={'test'} subSchemaName={'test'} />)

    const form = screen.getByTestId('Form')

    expect(form).toBeInTheDocument()
  })
})
