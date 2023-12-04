import React, { FC, useContext } from 'react'
import styles from './Form.module.less'
import Store, { useAppDispatch } from '@/stores/Store'
import { ServiceContext } from '@/contexts/ServiceContext'
import { SchemaSlice } from '@/stores/slices/SchemaSlice'

export interface FormProps {
  schemaName: string
  subSchemaName: string
}

const Form: FC<FormProps> = (props: FormProps) => {
  const { schemaName, subSchemaName } = props
  const schema = Store.getState().schemas.data[schemaName]?.[subSchemaName]
  const serviceContext = useContext(ServiceContext)
  if (schema == null) {
    // Fetch individual schema in case it missing
    useAppDispatch()(async (dispatch) => {
      const schema = await serviceContext.schemaService?.getSchema(schemaName, subSchemaName)
      dispatch(SchemaSlice.actions.saveSchema([schemaName, subSchemaName, schema]))
    })
  }
  // Get schema from redux store
  return (
    <div className={styles.Form} data-testid='Form'>
      Form Component
    </div>
  )
}

export default Form
