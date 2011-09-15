(ns tlog.app_servlet
  (:gen-class :extends javax.servlet.http.HttpServlet)
  (:use tlog.core)
  (:use [appengine-magic.servlet :only [make-servlet-service-method]]))


(defn -service [this request response]
  ((make-servlet-service-method tlog-app) this request response))