(ns tlog.app_servlet
  (:gen-class :extends javax.servlet.http.HttpServlet)
  (:use tlog.routes.routes)
  (:use [appengine-magic.servlet :only [make-servlet-service-method]]))


(defn -service [this request response]
  ((make-servlet-service-method tlog-app) this request response))
