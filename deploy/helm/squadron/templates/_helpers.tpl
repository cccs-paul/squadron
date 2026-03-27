{{/*
Squadron Helm chart template helpers
*/}}

{{/*
Expand the name of the chart.
*/}}
{{- define "squadron.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a fully qualified app name.
*/}}
{{- define "squadron.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version for chart label.
*/}}
{{- define "squadron.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Namespace to deploy into.
*/}}
{{- define "squadron.namespace" -}}
{{- default .Release.Namespace .Values.global.namespace }}
{{- end }}

{{/*
Common labels applied to every resource.
*/}}
{{- define "squadron.labels" -}}
helm.sh/chart: {{ include "squadron.chart" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: squadron
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}

{{/*
Per-service labels. Call with a dict: include "squadron.serviceLabels" (dict "root" . "service" "gateway")
*/}}
{{- define "squadron.serviceLabels" -}}
{{ include "squadron.labels" .root }}
app.kubernetes.io/name: {{ .service }}
app.kubernetes.io/instance: {{ .root.Release.Name }}-{{ .service }}
app.kubernetes.io/component: {{ .service }}
{{- end }}

{{/*
Per-service selector labels.
*/}}
{{- define "squadron.selectorLabels" -}}
app.kubernetes.io/name: {{ .service }}
app.kubernetes.io/instance: {{ .root.Release.Name }}-{{ .service }}
{{- end }}

{{/*
Service account name for a service.
*/}}
{{- define "squadron.serviceAccountName" -}}
squadron-{{ .service }}
{{- end }}

{{/*
Full image reference. Call with dict: include "squadron.image" (dict "root" . "image" .Values.gateway.image)
*/}}
{{- define "squadron.image" -}}
{{- $registry := .root.Values.global.imageRegistry -}}
{{- if $registry -}}
{{- printf "%s/%s:%s" $registry .image.repository (.image.tag | default "latest") -}}
{{- else -}}
{{- printf "%s:%s" .image.repository (.image.tag | default "latest") -}}
{{- end -}}
{{- end }}

{{/*
PostgreSQL JDBC URL for a service database.
*/}}
{{- define "squadron.jdbcUrl" -}}
jdbc:postgresql://squadron-postgresql:5432/{{ .dbName }}
{{- end }}

{{/*
Image pull secrets.
*/}}
{{- define "squadron.imagePullSecrets" -}}
{{- range .Values.global.imagePullSecrets }}
- name: {{ . }}
{{- end }}
{{- end }}
