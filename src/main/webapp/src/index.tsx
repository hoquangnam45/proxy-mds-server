import React from 'react'
import ReactDOM from 'react-dom/client'
import '@/index.module.less'
import reportWebVitals from '@/reportWebVitals'

import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import Routes from '@routes/Routes'
import { Provider } from 'react-redux'
import { Store } from '@stores/Store'

const root = ReactDOM.createRoot(document.getElementById('root')!)
root.render(
  <React.StrictMode>
    <Provider store={Store}>
      <RouterProvider router={createBrowserRouter(Routes)} />
    </Provider>
    ,
  </React.StrictMode>
)

// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals(console.log)
